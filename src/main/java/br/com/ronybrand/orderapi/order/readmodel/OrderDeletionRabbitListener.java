package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.commons.messaging.MessageParsing;
import br.com.ronybrand.orderapi.commons.messaging.MessagingMetrics;
import br.com.ronybrand.orderapi.commons.messaging.RetryLoop;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String LISTENER_NAME = "deletion";

    private final OrderProjectionService orderProjectionService;
    private final ObjectMapper objectMapper;
    private final MessagingMetrics messagingMetrics;

    public OrderDeletionRabbitListener(final OrderProjectionService orderProjectionService,
            @Qualifier("orderProjectionObjectMapper") final ObjectMapper objectMapper, final MessagingMetrics messagingMetrics) {
        this.orderProjectionService = orderProjectionService;
        this.objectMapper = objectMapper;
        this.messagingMetrics = messagingMetrics;
    }

    @Override
    public void onMessage(final Message message) {
        final OrderDeletionMessage deletionMessage;
        try {
            deletionMessage = parse(message);
        } catch (final MalformedOrderProjectionMessageException e) {
            log.warn("{} - sending straight to DLQ", e.getMessage());
            messagingMetrics.recordDlq(LISTENER_NAME);
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        deleteWithRetry(deletionMessage);
    }

    private OrderDeletionMessage parse(final Message message) {
        return MessageParsing.parseOrThrow(message.getBody(), objectMapper, OrderDeletionMessage.class,
                deletionMessage -> deletionMessage == null || deletionMessage.orderId() == null,
                "Malformed order deletion message", "Order deletion message is missing required fields",
                MalformedOrderProjectionMessageException::new);
    }

    private void deleteWithRetry(final OrderDeletionMessage message) {
        RetryLoop.run(OrderProjectionWriteException.class, OrderProjectionRetryPolicy.MAX_RETRIES,
                OrderProjectionRetryPolicy.INITIAL_BACKOFF, OrderProjectionRetryPolicy.BACKOFF_MULTIPLIER,
                () -> orderProjectionService.deleteById(message.orderId()),
                (attempt, maxAttempts, backoffMs, e) -> {
                    log.warn("Transient failure deleting order view (attempt {}/{}), retrying in {}ms: orderId={}",
                            attempt, maxAttempts, backoffMs, message.orderId(), e);
                    messagingMetrics.recordRetry(LISTENER_NAME);
                },
                (attempts, e) -> {
                    log.error("Failed to delete order view after {} attempts, sending to DLQ: orderId={}",
                            attempts, message.orderId(), e);
                    messagingMetrics.recordDlq(LISTENER_NAME);
                });
    }
}
