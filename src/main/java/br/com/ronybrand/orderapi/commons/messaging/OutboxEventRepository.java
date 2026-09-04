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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    long countByStatusIn(Collection<OutboxStatus> statuses);

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