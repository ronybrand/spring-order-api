package br.com.ronybrand.orderapi.customer;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    boolean existsByTaxId(String taxId);

    boolean existsByPassportNumber(String passportNumber);

    boolean existsByTaxIdAndIdNot(String taxId, UUID id);

    boolean existsByPassportNumberAndIdNot(String passportNumber, UUID id);

    /**
     * {@code SELECT ... FOR UPDATE} - used by {@code CustomerService.delete} so its
     * "no active orders" check and the soft-delete it guards are atomic with respect to a
     * concurrent {@code OrderService.create} for the same customer (which takes the complementary
     * {@link #findByIdForShare}). Without both sides locked, Postgres's FK-driven
     * {@code FOR KEY SHARE} on insert does not conflict with a plain row update, so the two
     * operations can interleave and leave an active order under a deleted customer (DOMAIN.md §4.8).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

    /**
     * {@code SELECT ... FOR SHARE} - used by {@code OrderService.create} so it blocks behind (and
     * then correctly re-reads after) a concurrent {@code CustomerService.delete} holding
     * {@link #findByIdForUpdate}, instead of racing it. Multiple concurrent creates for the same
     * customer are still compatible with each other under this mode.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForShare(@Param("id") UUID id);
}
