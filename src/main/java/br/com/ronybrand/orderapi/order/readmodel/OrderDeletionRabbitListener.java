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
 * Counterpart to {@link OrderProjectionRabbitListener} for the delete queue - same classification
 * and retry contract (malformed payload straight to DLQ, transient failure retried with backoff up
 * to {@link OrderProjectionRetryPolicy#MAX_RETRIES}), just deleting instead of upserting.
 *
 * <p>Implements {@link MessageListener} directly (not {@code @RabbitListener}), same as
 * {@link OrderProjectionRabbitListener}, so the raw message body is available for classification
 * before any automatic conversion could throw first.
 */
@Slf4j
@Component
public class OrderDeletionRabbitListener implements MessageListener {

    private final OrderProjectionService orderProjectionService;
    private final ObjectMapper objectMapper;

    public OrderDeletionRabbitListener(final OrderProjectionService orderProjectionService,
            @Qualifier("orderProjectionObjectMapper") final ObjectMapper objectMapper) {
        this.orderProjectionService = orderProjectionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(final Message message) {
        final OrderDeletionMessage deletionMessage;
        try {
            deletionMessage = parse(message);
        } catch (final MalformedOrderProjectionMessageException e) {
            log.warn("{} - sending straight to DLQ", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        deleteWithRetry(deletionMessage);
    }

    private OrderDeletionMessage parse(final Message message) {
        final OrderDeletionMessage deletionMessage;
        try {
            deletionMessage = objectMapper.readValue(message.getBody(), OrderDeletionMessage.class);
        } catch (final IOException | RuntimeException e) {
            throw new MalformedOrderProjectionMessageException("Malformed order deletion message", e);
        }
        if (deletionMessage == null || deletionMessage.orderId() == null) {
            throw new MalformedOrderProjectionMessageException("Order deletion message is missing required fields", null);
        }
        return deletionMessage;
    }

    private void deleteWithRetry(final OrderDeletionMessage message) {
        long backoffMs = OrderProjectionRetryPolicy.INITIAL_BACKOFF.toMillis();
        for (int attempt = 1; ; attempt++) {
            try {
                orderProjectionService.deleteById(message.orderId());
                return;
            } catch (final OrderProjectionWriteException e) {
                if (attempt > OrderProjectionRetryPolicy.MAX_RETRIES) {
                    log.error("Failed to delete order view after {} attempts, sending to DLQ: orderId={}",
                            attempt, message.orderId(), e);
                    throw new AmqpRejectAndDontRequeueException("Exhausted retries", e);
                }
                log.warn("Transient failure deleting order view (attempt {}/{}), retrying in {}ms: orderId={}",
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
            throw new IllegalStateException("Interrupted while waiting to retry order view delete", e);
        }
    }
}
