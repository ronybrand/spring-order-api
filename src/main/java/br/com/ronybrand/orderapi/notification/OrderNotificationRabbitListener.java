package br.com.ronybrand.orderapi.notification;

import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Implements {@link MessageListener} directly (not {@code @RabbitListener}) so the raw message
 * body is available for classification before any conversion could throw first.
 *
 * <p>Retry is a manual loop here rather than a Spring AMQP retry advice/interceptor - simpler to
 * reason about and test, and avoids depending on a specific interceptor/retry-library
 * integration. It still satisfies the same contract (DOMAIN.md §5): malformed payload never
 * retried, transient failure retried with backoff up to {@link NotificationRetryPolicy#MAX_RETRIES},
 * then rejected without requeue - {@code x-dead-letter-exchange} on the queue routes it to the
 * DLQ either way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationRabbitListener implements MessageListener {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(final Message message) {
        final OrderStatusChangedEvent event;
        try {
            event = parse(message);
        } catch (final MalformedOrderStatusMessageException e) {
            log.warn("{} - sending straight to DLQ", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        sendWithRetry(event);
    }

    private OrderStatusChangedEvent parse(final Message message) {
        final OrderStatusChangedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), OrderStatusChangedEvent.class);
        } catch (final IOException | RuntimeException e) {
            throw new MalformedOrderStatusMessageException("Malformed order status message", e);
        }
        if (isMissingRequiredField(event)) {
            throw new MalformedOrderStatusMessageException("Order status message is missing required fields", null);
        }
        return event;
    }

    private boolean isMissingRequiredField(final OrderStatusChangedEvent event) {
        return event == null
                || event.orderId() == null
                || event.customerEmail() == null
                || event.customerName() == null
                || event.oldStatus() == null
                || event.newStatus() == null
                || event.totalAmount() == null
                || event.changedAt() == null;
    }

    private void sendWithRetry(final OrderStatusChangedEvent event) {
        long backoffMs = NotificationRetryPolicy.INITIAL_BACKOFF.toMillis();
        for (int attempt = 1; ; attempt++) {
            try {
                emailService.sendOrderStatusEmail(event);
                return;
            } catch (final EmailSendingException e) {
                if (attempt > NotificationRetryPolicy.MAX_RETRIES) {
                    log.error("Failed to send notification after {} attempts, sending to DLQ: orderId={}",
                            attempt, event.orderId(), e);
                    throw new AmqpRejectAndDontRequeueException("Exhausted retries", e);
                }
                log.warn("Transient failure sending notification (attempt {}/{}), retrying in {}ms: orderId={}",
                        attempt, NotificationRetryPolicy.MAX_RETRIES + 1, backoffMs, event.orderId(), e);
                sleep(backoffMs);
                backoffMs = (long) (backoffMs * NotificationRetryPolicy.BACKOFF_MULTIPLIER);
            }
        }
    }

    private void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry notification", e);
        }
    }
}
