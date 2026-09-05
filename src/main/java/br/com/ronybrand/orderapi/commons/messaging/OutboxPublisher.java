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
