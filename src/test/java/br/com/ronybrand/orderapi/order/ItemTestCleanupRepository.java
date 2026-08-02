package br.com.ronybrand.orderapi.order;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-only hard delete, bypassing any soft-delete filtering - a real
 * {@code DELETE FROM item} has no legitimate use in production code (see
 * {@link OrderTestCleanupRepository} for why this is needed alongside plain {@code deleteAll()}).
 */
public interface ItemTestCleanupRepository extends JpaRepository<Item, UUID> {

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM item", nativeQuery = true)
    void deleteAllHard();
}
