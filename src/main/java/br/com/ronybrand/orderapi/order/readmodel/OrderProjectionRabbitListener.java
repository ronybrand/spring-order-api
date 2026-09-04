package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.commons.messaging.MessageParsing;
import br.com.ronybrand.orderapi.commons.messaging.RetryLoop;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        return MessageParsing.parseOrThrow(message.getBody(), objectMapper, OrderProjectionMessage.class,
                this::isMissingRequiredField, "Malformed order projection message",
                "Order projection message is missing required fields", MalformedOrderProjectionMessageException::new);
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
        RetryLoop.run(OrderProjectionWriteException.class, OrderProjectionRetryPolicy.MAX_RETRIES,
                OrderProjectionRetryPolicy.INITIAL_BACKOFF, OrderProjectionRetryPolicy.BACKOFF_MULTIPLIER,
                () -> orderProjectionService.upsert(message),
                (attempt, maxAttempts, backoffMs, e) -> log.warn(
                        "Transient failure upserting order view (attempt {}/{}), retrying in {}ms: orderId={}",
                        attempt, maxAttempts, backoffMs, message.orderId(), e),
                (attempts, e) -> log.error("Failed to upsert order view after {} attempts, sending to DLQ: orderId={}",
                        attempts, message.orderId(), e));
    }
}
