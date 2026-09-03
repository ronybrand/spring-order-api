package br.com.ronybrand.orderapi.order;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    boolean existsByCustomerId(UUID customerId);

    /**
     * Fetch-joins {@code customer} and {@code items} (both {@code FetchType.LAZY}) so
     * {@code OrderChangedEventListener} can read them after the original request's transaction has
     * already committed, without a {@code LazyInitializationException}.
     */
    @Query("select o from Order o join fetch o.customer left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);
}
