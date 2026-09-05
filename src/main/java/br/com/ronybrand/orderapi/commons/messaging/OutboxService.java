package br.com.ronybrand.orderapi.commons.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final MessagingMetrics messagingMetrics;

    public OutboxService(final OutboxEventRepository repository,
            @Qualifier("orderStatusObjectMapper") final ObjectMapper objectMapper,
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
        final List<OutboxEvent> events = repository.findClaimable(now, now.minusMinutes(5), PageRequest.of(0, BATCH_SIZE));
        events.forEach(event -> event.markProcessing(now));
        return events;
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