package br.com.ronybrand.orderapi.commons.messaging;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.ToIntFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes stale rows from the {@code outbox_events} table (see ADR 0006's consequences section) so
 * it doesn't grow indefinitely, and so the plaintext PII embedded in each event's payload (e.g.
 * customer email/name) doesn't linger forever either.
 *
 * <p>{@code PUBLISHED} rows are deleted after the short {@code app.outbox.cleanup.retention}
 * window - once delivered, they have no further purpose. {@code FAILED} rows get a much longer
 * {@code app.outbox.cleanup.failed-retention} window instead of an immediate one, so they stay
 * available for manual investigation for a while - but they are not kept forever, since their
 * payload carries the same PII exposure as any other row.
 */
@Slf4j
@Component
public class OutboxCleanupJob {

    private final OutboxEventRepository repository;
    private final Duration retention;
    private final Duration failedRetention;
    private final int batchSize;

    public OutboxCleanupJob(final OutboxEventRepository repository,
            @Value("${app.outbox.cleanup.retention:7d}") final Duration retention,
            @Value("${app.outbox.cleanup.failed-retention:90d}") final Duration failedRetention,
            @Value("${app.outbox.cleanup.batch-size:500}") final int batchSize) {
        this.repository = repository;
        this.retention = retention;
        this.failedRetention = failedRetention;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final int publishedDeleted = deleteInBatches(
                cutoff -> repository.deletePublishedBefore(OutboxStatus.PUBLISHED.name(), cutoff, batchSize),
                now.minus(retention));
        if (publishedDeleted > 0) {
            log.info("Outbox cleanup deleted {} published event(s) older than {}", publishedDeleted,
                    now.minus(retention));
        }

        final int failedDeleted = deleteInBatches(
                cutoff -> repository.deleteFailedBefore(OutboxStatus.FAILED.name(), cutoff, batchSize),
                now.minus(failedRetention));
        if (failedDeleted > 0) {
            log.info("Outbox cleanup deleted {} failed event(s) older than {}", failedDeleted,
                    now.minus(failedRetention));
        }
    }

    private int deleteInBatches(final ToIntFunction<LocalDateTime> deleteBatch, final LocalDateTime cutoff) {
        int deletedInBatch;
        int totalDeleted = 0;
        do {
            deletedInBatch = deleteBatch.applyAsInt(cutoff);
            totalDeleted += deletedInBatch;
        } while (deletedInBatch == batchSize);
        return totalDeleted;
    }
}
