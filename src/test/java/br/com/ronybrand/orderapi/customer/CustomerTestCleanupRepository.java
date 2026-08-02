package br.com.ronybrand.orderapi.customer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-only hard delete, bypassing {@code @SQLRestriction} - see
 * {@link br.com.ronybrand.orderapi.order.OrderTestCleanupRepository} for why plain
 * {@code deleteAll()} is not enough once soft-delete tests exist.
 */
public interface CustomerTestCleanupRepository extends JpaRepository<Customer, UUID> {

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM customer", nativeQuery = true)
    void deleteAllHard();
}
