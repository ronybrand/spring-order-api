package br.com.ronybrand.orderapi.order.readmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Implements {@link MessageListener} directly (not {@code @RabbitListener}) so the raw message
 * body is available for classification before any conversion could throw first - same rationale
 * as {@code notification.OrderNotificationRabbitListener}.
 *
 * <p>Retry is a manual loop, not a Spring AMQP retry advice/interceptor: malformed payload never
 * retried, transient failure retried with backoff up to
 * {@link OrderProjectionRetryPolicy#MAX_RETRIES}, then rejected without requeue -
 * {@code x-dead-letter-exchange} on the queue routes it to the DLQ either way.
 */
@Slf4j
@Component
public class OrderProjectionRabbitListener implements MessageListener {

    private final OrderProjectionService orderProjectionService;
    private final ObjectMapper objectMapper;

    public OrderProjectionRabbitListener(final OrderProjectionService orderProjectionService,
            @Qualifier("orderProjectionObjectMapper") final ObjectMapper objectMapper) {
        this.orderProjectionService = orderProjectionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(final Message message) {
        final OrderProjectionMessage projectionMessage;
        try {
            projectionMessage = parse(message);
        } catch (final MalformedOrderProjectionMessageException e) {
            log.warn("{} - sending straight to DLQ", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        upsertWithRetry(projectionMessage);
    }

    private OrderProjectionMessage parse(final Message message) {
        final OrderProjectionMessage projectionMessage;
        try {
            projectionMessage = objectMapper.readValue(message.getBody(), OrderProjectionMessage.class);
        } catch (final IOException | RuntimeException e) {
            throw new MalformedOrderProjectionMessageException("Malformed order projection message", e);
        }
        if (isMissingRequiredField(projectionMessage)) {
            throw new MalformedOrderProjectionMessageException("Order projection message is missing required fields", null);
        }
        return projectionMessage;
    }

    private boolean isMissingRequiredField(final OrderProjectionMessage message) {
        return message == null
                || message.orderId() == null
                || message.customerId() == null
                || message.status() == null
                || message.items() == null
                || message.totalAmount() == null
                || message.updatedAt() == null;
    }

    private void upsertWithRetry(final OrderProjectionMessage message) {
        long backoffMs = OrderProjectionRetryPolicy.INITIAL_BACKOFF.toMillis();
        for (int attempt = 1; ; attempt++) {
            try {
                orderProjectionService.upsert(message);
                return;
            } catch (final OrderProjectionWriteException e) {
                if (attempt > OrderProjectionRetryPolicy.MAX_RETRIES) {
                    log.error("Failed to upsert order view after {} attempts, sending to DLQ: orderId={}",
                            attempt, message.orderId(), e);
                    throw new AmqpRejectAndDontRequeueException("Exhausted retries", e);
                }
                log.warn("Transient failure upserting order view (attempt {}/{}), retrying in {}ms: orderId={}",
                        attempt, OrderProjectionRetryPolicy.MAX_RETRIES + 1, backoffMs, message.orderId(), e);
                sleep(backoffMs);
                backoffMs = (long) (backoffMs * OrderProjectionRetryPolicy.BACKOFF_MULTIPLIER);
            }
        }
    }

    private void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry order view upsert", e);
        }
    }
}
