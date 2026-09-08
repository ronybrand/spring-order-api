package br.com.ronybrand.orderapi.commons.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the {@code outbox_events} table (see ADR 0006) and publishes each claimed batch to
 * RabbitMQ - an event is only ever marked {@code PUBLISHED} after the broker send itself succeeds,
 * so a crash between claim and send just leaves it {@code PROCESSING} for another instance's lease
 * to reclaim later, never silently lost.
 *
 * <p>Deliberately one broker send plus one DB commit per event (via {@link OutboxService#markPublished}/
 * {@link OutboxService#markFailed}), not a single batched commit for the whole claimed batch - at
 * this app's scale (default batch size 50, 1s poll) that overhead is not a measured bottleneck, and
 * per-event commits keep each event's outcome independent and immediately visible (no
 * partially-applied batch to reason about if the process dies mid-loop). If outbox throughput ever
 * does become a real bottleneck, batch the successful outcomes into one bulk
 * {@code UPDATE ... WHERE id IN (...)} (status/published_at) after the send loop instead of N
 * individual saves - failures still need per-event handling (distinct backoff/error text), so only
 * the success path is a candidate for batching.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxService outboxService;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingMetrics messagingMetrics;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    public void publishPending() {
        final List<OutboxEvent> events = outboxService.claimBatch();
        for (final OutboxEvent event : events) {
            try {
                final MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                rabbitTemplate.send(event.getExchangeName(), event.getRoutingKey(),
                        new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties));
                outboxService.markPublished(event);
                messagingMetrics.recordOutboxPublished(event.getEventType());
            } catch (final RuntimeException exception) {
                outboxService.markFailed(event, exception);
                log.error("Outbox event publish failed: eventId={}, eventType={}, attempt={}",
                        event.getId(), event.getEventType(), event.getAttempts() + 1, exception);
                messagingMetrics.recordOutboxPublishFailure(event.getEventType());
                if (event.getStatus() == OutboxStatus.FAILED) {
                    messagingMetrics.recordOutboxPermanentlyFailed(event.getEventType());
                }
            }
        }
    }
}
