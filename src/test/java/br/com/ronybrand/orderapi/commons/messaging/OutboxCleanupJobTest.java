package br.com.ronybrand.orderapi.commons.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Unit-level coverage for the batching/looping logic of {@link OutboxCleanupJob}; the actual
 * {@code DELETE ... LIMIT} SQL is covered against a real database by
 * {@link OutboxEventRepositoryIT#deletePublishedBefore_ShouldDeleteOnlyPublishedEventsOlderThanCutoff},
 * {@link OutboxEventRepositoryIT#deletePublishedBefore_ShouldRespectLimit_WhenMoreEligibleRowsThanBatchSize}
 * and their {@code deleteFailedBefore} counterparts.
 */
class OutboxCleanupJobTest {

    private static final int BATCH_SIZE = 500;

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxCleanupJob job =
            new OutboxCleanupJob(repository, Duration.ofDays(7), Duration.ofDays(90), BATCH_SIZE);

    @Test
    void cleanup_ShouldDeleteNothing_WhenNoEligibleRowsExist() {
        when(repository.deletePublishedBefore(eq(OutboxStatus.PUBLISHED.name()), any(LocalDateTime.class), eq(BATCH_SIZE)))
                .thenReturn(0);
        when(repository.deleteFailedBefore(eq(OutboxStatus.FAILED.name()), any(LocalDateTime.class), eq(BATCH_SIZE)))
                .thenReturn(0);

        job.cleanup();

        verify(repository, times(1)).deletePublishedBefore(eq(OutboxStatus.PUBLISHED.name()), any(LocalDateTime.class),
                eq(BATCH_SIZE));
        verify(repository, times(1)).deleteFailedBefore(eq(OutboxStatus.FAILED.name()), any(LocalDateTime.class),
                eq(BATCH_SIZE));
    }

    @Test
    void cleanup_ShouldKeepDeletingPublishedBatches_UntilABatchComesBackSmallerThanTheBatchSize() {
        when(repository.deletePublishedBefore(eq(OutboxStatus.PUBLISHED.name()), any(LocalDateTime.class), eq(BATCH_SIZE)))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 42);
        when(repository.deleteFailedBefore(any(), any(), anyInt())).thenReturn(0);

        job.cleanup();

        verify(repository, times(3)).deletePublishedBefore(eq(OutboxStatus.PUBLISHED.name()), any(LocalDateTime.class),
                eq(BATCH_SIZE));
    }

    @Test
    void cleanup_ShouldKeepDeletingFailedBatches_UntilABatchComesBackSmallerThanTheBatchSize() {
        when(repository.deletePublishedBefore(any(), any(), anyInt())).thenReturn(0);
        when(repository.deleteFailedBefore(eq(OutboxStatus.FAILED.name()), any(LocalDateTime.class), eq(BATCH_SIZE)))
                .thenReturn(BATCH_SIZE, 7);

        job.cleanup();

        verify(repository, times(2)).deleteFailedBefore(eq(OutboxStatus.FAILED.name()), any(LocalDateTime.class),
                eq(BATCH_SIZE));
    }

    @Test
    void cleanup_ShouldTargetPublishedAndFailedEvents_WithIndependentCutoffs() {
        when(repository.deletePublishedBefore(any(), any(), anyInt())).thenReturn(0);
        when(repository.deleteFailedBefore(any(), any(), anyInt())).thenReturn(0);

        job.cleanup();

        verify(repository).deletePublishedBefore(eq(OutboxStatus.PUBLISHED.name()), any(LocalDateTime.class), anyInt());
        verify(repository).deleteFailedBefore(eq(OutboxStatus.FAILED.name()), any(LocalDateTime.class), anyInt());
    }
}
