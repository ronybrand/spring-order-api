package br.com.ronybrand.orderapi.notification;

import br.com.ronybrand.orderapi.commons.messaging.MessageParsing;
import br.com.ronybrand.orderapi.commons.messaging.MessagingMetrics;
import br.com.ronybrand.orderapi.commons.messaging.RetryLoop;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * succeeding and the ack reaching the broker), or the same message landing on two horizontally
 * scaled instances, would otherwise duplicate the email to the customer. Guarded with an atomic
 * Redis claim ({@code SET NX} via {@link org.springframework.data.redis.core.ValueOperations#setIfAbsent},
 * not a separate check-then-set) taken *before* sending, so two consumers racing on the same key
 * can never both win it. If the send ends up exhausting retries and going to the DLQ, the claim is
 * released so a future reprocessing of that message is never wrongly skipped as "already sent".
 */
@Slf4j
@Component
public class OrderNotificationRabbitListener implements MessageListener {

    private static final Duration IDEMPOTENCY_KEY_TTL = Duration.ofHours(24);
    private static final String LISTENER_NAME = "notification";

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MessagingMetrics messagingMetrics;

    public OrderNotificationRabbitListener(final EmailService emailService,
            @Qualifier("orderStatusObjectMapper") final ObjectMapper objectMapper,
            final StringRedisTemplate stringRedisTemplate, final MessagingMetrics messagingMetrics) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.messagingMetrics = messagingMetrics;
    }

    @Override
    public void onMessage(final Message message) {
        final OrderStatusChangedEvent event;
        try {
            event = parse(message);
        } catch (final MalformedOrderStatusMessageException e) {
            log.warn("{} - sending straight to DLQ", e.getMessage());
            messagingMetrics.recordDlq(LISTENER_NAME);
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }

        final String idempotencyKey = idempotencyKey(event);
        final boolean claimed = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_KEY_TTL));
        if (!claimed) {
            log.info("Notification already sent or in flight, skipping: orderId={}", event.orderId());
            messagingMetrics.recordIdempotencySkip(LISTENER_NAME);
            return;
        }
        try {
            sendWithRetry(event);
        } catch (final RuntimeException e) {
            stringRedisTemplate.delete(idempotencyKey);
            throw e;
        }
    }

    private String idempotencyKey(final OrderStatusChangedEvent event) {
        return "notification:sent:" + event.orderId() + ":" + event.newStatus();
    }

    private OrderStatusChangedEvent parse(final Message message) {
        return MessageParsing.parseOrThrow(message.getBody(), objectMapper, OrderStatusChangedEvent.class,
                this::isMissingRequiredField, "Malformed order status message",
                "Order status message is missing required fields", MalformedOrderStatusMessageException::new);
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
        RetryLoop.run(EmailSendingException.class, NotificationRetryPolicy.MAX_RETRIES,
                NotificationRetryPolicy.INITIAL_BACKOFF, NotificationRetryPolicy.BACKOFF_MULTIPLIER,
                () -> emailService.sendOrderStatusEmail(event),
                (attempt, maxAttempts, backoffMs, e) -> {
                    log.warn("Transient failure sending notification (attempt {}/{}), retrying in {}ms: orderId={}",
                            attempt, maxAttempts, backoffMs, event.orderId(), e);
                    messagingMetrics.recordRetry(LISTENER_NAME);
                },
                (attempts, e) -> {
                    log.error("Failed to send notification after {} attempts, sending to DLQ: orderId={}",
                            attempts, event.orderId(), e);
                    messagingMetrics.recordDlq(LISTENER_NAME);
                });
    }
}
