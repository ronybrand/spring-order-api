package br.com.ronybrand.orderapi.commons.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private static final int BATCH_SIZE = 50;
    private static final List<OutboxStatus> BACKLOG_STATUSES = List.of(OutboxStatus.PENDING, OutboxStatus.PROCESSING);

    /**
     * How long a claimed-but-not-yet-published row may stay {@code PROCESSING} before
     * {@link #claimBatch} reclaims it as abandoned. Exposed as a named constant (not an inline
     * literal) because {@code notification.OrderNotificationRabbitListener} deliberately mirrors
     * this exact value for its own Redis claim lease - referencing this constant, rather than
     * duplicating the duration and relying on a comment to keep the two in sync.
     */
    public static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final MessagingMetrics messagingMetrics;

    public OutboxService(final OutboxEventRepository repository,
            @Qualifier("outboxObjectMapper") final ObjectMapper objectMapper,
            final MessagingMetrics messagingMetrics) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.messagingMetrics = messagingMetrics;
    }

    @PostConstruct
    void registerBacklogGauge() {
        messagingMetrics.registerOutboxBacklogGauge(() -> repository.countByStatusIn(BACKLOG_STATUSES));
    }

    public void enqueue(final String eventType, final UUID aggregateId, final String exchange, final String routingKey,
            final Object event) {
        try {
            repository.save(OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .exchangeName(exchange)
                    .routingKey(routingKey)
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .availableAt(now())
                    .createdAt(now())
                    .build());
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event", e);
        }
    }

    @Transactional
    List<OutboxEvent> claimBatch() {
        final LocalDateTime now = now();
        final List<OutboxEvent> events =
                repository.findClaimable(now, now.minus(PROCESSING_LEASE), PageRequest.of(0, BATCH_SIZE));
        final List<OutboxEvent> claimed = new ArrayList<>(events.size());
        for (final OutboxEvent event : events) {
            if (event.getStatus() == OutboxStatus.PROCESSING) {
                // Reclaiming a row still PROCESSING past its lease: the previous claim never
                // reached markPublished/markFailed (a crash, or a send throwing something other
                // than RuntimeException), so this reclaim itself counts as the lost attempt -
                // otherwise attempts would stay at zero and a catastrophically failing payload
                // would retry forever, every lease window, without ever reaching FAILED.
                event.markReclaimed(now);
                if (event.getStatus() == OutboxStatus.FAILED) {
                    messagingMetrics.recordOutboxPermanentlyFailed(event.getEventType());
                    continue;
                }
            } else {
                event.markProcessing(now);
            }
            claimed.add(event);
        }
        return claimed;
    }

    @Transactional
    void markPublished(final OutboxEvent event) {
        event.markPublished(now());
        repository.save(event);
    }

    @Transactional
    void markFailed(final OutboxEvent event, final RuntimeException exception) {
        event.markRetry(now().plusSeconds(Math.min(60, 1L << Math.min(event.getAttempts(), 6))),
                exception.getClass().getSimpleName() + ": " + exception.getMessage());
        repository.save(event);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}