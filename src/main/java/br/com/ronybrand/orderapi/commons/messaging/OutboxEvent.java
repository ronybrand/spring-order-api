package br.com.ronybrand.orderapi.commons.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "exchange_name", nullable = false)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public void markProcessing(final LocalDateTime now) {
        status = OutboxStatus.PROCESSING;
        lockedAt = now;
    }

    public void markPublished(final LocalDateTime now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lockedAt = null;
        lastError = null;
    }

    public void markRetry(final LocalDateTime nextAvailableAt, final String error) {
        attempts++;
        status = attempts >= 5 ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        availableAt = nextAvailableAt;
        lockedAt = null;
        lastError = error;
    }
}