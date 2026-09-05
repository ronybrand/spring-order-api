package br.com.ronybrand.orderapi.commons.messaging;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes {@code PUBLISHED} rows older than {@code app.outbox.cleanup.retention} from the
 * {@code outbox_events} table (see ADR 0006's consequences section) so it doesn't grow
 * indefinitely. {@code FAILED} rows are deliberately left alone - they need manual investigation,
 * not a retention window.
 */
@Slf4j
@Component
public class OutboxCleanupJob {

    private final OutboxEventRepository repository;
    private final Duration retention;
    private final int batchSize;

    public OutboxCleanupJob(final OutboxEventRepository repository,
            @Value("${app.outbox.cleanup.retention:7d}") final Duration retention,
            @Value("${app.outbox.cleanup.batch-size:500}") final int batchSize) {
        this.repository = repository;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        final LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minus(retention);
        int deletedInBatch;
        int totalDeleted = 0;
        do {
            deletedInBatch = repository.deletePublishedBefore(OutboxStatus.PUBLISHED.name(), cutoff, batchSize);
            totalDeleted += deletedInBatch;
        } while (deletedInBatch == batchSize);
        if (totalDeleted > 0) {
            log.info("Outbox cleanup deleted {} published event(s) older than {}", totalDeleted, cutoff);
        }
    }
}
