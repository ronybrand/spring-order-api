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

    private static final int MAX_ATTEMPTS = 5;

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

    /**
     * Called instead of {@link #markProcessing} when {@code OutboxEventRepository#findClaimable}
     * reclaims a row still {@code PROCESSING} past its lease: the previous claim died before ever
     * reaching {@link #markPublished} or {@link #markRetry} (a hard crash, or a broker send
     * throwing something other than {@code RuntimeException}), so nothing bumped {@code attempts}
     * for that lost attempt. Counting the reclaim itself here is what lets a catastrophically
     * failing payload still reach the {@code FAILED} attempts cap, instead of being reclaimed and
     * retried forever every lease window with {@code attempts} stuck at zero.
     */
    @SuppressWarnings("PMD.NullAssignment")
    public void markReclaimed(final LocalDateTime now) {
        attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            status = OutboxStatus.FAILED;
            lockedAt = null;
        } else {
            status = OutboxStatus.PROCESSING;
            lockedAt = now;
        }
    }

    /**
     * Nulls {@code lockedAt}/{@code lastError} deliberately, not a code smell PMD's
     * {@code NullAssignment} rule is built to catch: a published event holds neither a processing
     * lease nor a stale error from an earlier retry.
     */
    @SuppressWarnings("PMD.NullAssignment")
    public void markPublished(final LocalDateTime now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lockedAt = null;
        lastError = null;
    }

    /** See {@link #markPublished} - {@code lockedAt} is cleared so the row is claimable again. */
    @SuppressWarnings("PMD.NullAssignment")
    public void markRetry(final LocalDateTime nextAvailableAt, final String error) {
        attempts++;
        status = attempts >= 5 ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        availableAt = nextAvailableAt;
        lockedAt = null;
        lastError = error;
    }
}