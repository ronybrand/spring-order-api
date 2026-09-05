package br.com.ronybrand.orderapi.commons.messaging;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    long countByStatusIn(Collection<OutboxStatus> statuses);

    /**
     * Deletes at most {@code limit} rows per call (via the {@code LIMIT} subquery below, since
     * Postgres has no {@code DELETE ... LIMIT} syntax of its own) so a large backlog is cleaned up
     * in bounded batches instead of one long-running delete locking the table.
     * {@link OutboxCleanupJob} calls this in a loop until it returns fewer rows than requested.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from outbox_events where id in "
            + "(select id from outbox_events where status = :status and published_at < :cutoff limit :limit)",
            nativeQuery = true)
    int deletePublishedBefore(String status, LocalDateTime cutoff, int limit);

    /**
     * Same batching contract as {@link #deletePublishedBefore}, but cutting off on {@code created_at}:
     * {@code FAILED} rows have no {@code published_at} (they never published), so age since creation
     * is the only timestamp available to bound their (much longer) retention window.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from outbox_events where id in "
            + "(select id from outbox_events where status = :status and created_at < :cutoff limit :limit)",
            nativeQuery = true)
    int deleteFailedBefore(String status, LocalDateTime cutoff, int limit);

    /**
     * {@code PESSIMISTIC_WRITE} plus the Hibernate-specific {@code jakarta.persistence.lock.timeout}
     * hint of {@code -2} (Hibernate's magic value for {@code SKIP LOCKED}, no portable JPA API for
     * it) - required so multiple {@code OutboxPublisher} instances polling concurrently each claim a
     * disjoint batch instead of blocking on each other's row locks. Without this hint the plain
     * pessimistic lock would make concurrent publishers serialize on {@code findClaimable}, one
     * waiting for the other's transaction to commit/rollback before it could even see which rows
     * were already claimed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where (e.status = br.com.ronybrand.orderapi.commons.messaging.OutboxStatus.PENDING and e.availableAt <= :now) "
            + "or (e.status = br.com.ronybrand.orderapi.commons.messaging.OutboxStatus.PROCESSING and e.lockedAt < :lease) "
            + "order by e.createdAt")
    List<OutboxEvent> findClaimable(final LocalDateTime now, final LocalDateTime lease, final Pageable pageable);
}