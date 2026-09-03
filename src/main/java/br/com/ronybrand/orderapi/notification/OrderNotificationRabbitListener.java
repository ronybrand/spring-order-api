package br.com.ronybrand.orderapi.notification;

import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 *
 * <p>Sending an email is not naturally idempotent (unlike the read-model's Mongo upsert) - a
 * RabbitMQ redelivery of an already-processed message (e.g. connection dropped between the send
 * succeeding and the ack reaching the broker) would otherwise duplicate the email to the
 * customer. Guarded with a Redis idempotency key (`SET NX` semantics via a plain existence check
 * before sending, since this consumer is single-threaded - no concurrent claim race to close),
 * written only *after* the send succeeds so a message that ends up retried/DLQ'd is never
 * wrongly marked as already sent.
 */
@Slf4j
@Component
public class OrderNotificationRabbitListener implements MessageListener {

    private static final Duration IDEMPOTENCY_KEY_TTL = Duration.ofHours(24);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public OrderNotificationRabbitListener(final EmailService emailService,
            @Qualifier("orderStatusObjectMapper") final ObjectMapper objectMapper,
            final StringRedisTemplate stringRedisTemplate) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void onMessage(final Message message) {
        final OrderStatusChangedEvent event;
        try {
            event = parse(message);
        } catch (final MalformedOrderStatusMessageException e) {
            log.warn("{} - sending straight to DLQ", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }

        final String idempotencyKey = idempotencyKey(event);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(idempotencyKey))) {
            log.info("Notification already sent, skipping: orderId={}", event.orderId());
            return;
        }
        sendWithRetry(event);
        stringRedisTemplate.opsForValue().set(idempotencyKey, "1", IDEMPOTENCY_KEY_TTL);
    }

    private String idempotencyKey(final OrderStatusChangedEvent event) {
        return "notification:sent:" + event.orderId() + ":" + event.newStatus();
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
