package br.com.ronybrand.orderapi.order;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-only hard delete. {@code Order}'s {@code @SQLRestriction("deleted_at IS NULL")} means the
 * production {@code deleteAll()} never sees rows a previous test already soft-deleted, leaving
 * them behind to block a subsequent {@code CustomerTestCleanupRepository} hard-delete via the
 * {@code orders.customer_id} FK. Delete items before orders (no {@code ON DELETE CASCADE} on
 * {@code item.order_id}).
 */
public interface OrderTestCleanupRepository extends JpaRepository<Order, UUID> {

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM orders", nativeQuery = true)
    void deleteAllHard();
}
