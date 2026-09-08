package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Real integration against MongoDB (Testcontainers, shared with the rest of the suite via
 * {@link AbstractAuthIntegrationTest}) - not just an {@code OrderViewRepository} mock like
 * {@link OrderProjectionServiceTest}. Extends {@code AbstractAuthIntegrationTest} even though it
 * never touches HTTP/JWT, because a full-context {@code @SpringBootTest} also boots
 * Postgres/Liquibase regardless of which repository the test itself uses - and, for the same
 * reason, still needs {@link TestSecurityConfig} so {@code SecurityFilterChain} bean creation
 * doesn't try to reach a real Keycloak for OIDC discovery.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class OrderProjectionIT extends AbstractAuthIntegrationTest {

    @Autowired
    private OrderProjectionService orderProjectionService;

    @Autowired
    private OrderViewRepository orderViewRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static OrderProjectionMessage message(final UUID orderId) {
        final OrderProjectionItem item = new OrderProjectionItem(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        return new OrderProjectionMessage(orderId, UUID.randomUUID(), OrderStatus.CONFIRMED, List.of(item),
                new BigDecimal("20.00"), LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void upsert_ShouldPersistToRealMongo() {
        final UUID orderId = UUID.randomUUID();

        orderProjectionService.upsert(message(orderId));

        final OrderView persisted = orderViewRepository.findById(orderId.toString()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(persisted.getItems()).hasSize(1);
    }

    @Test
    void upsert_ShouldBeIdempotent_WhenReprocessingTheSameMessage() {
        final UUID orderId = UUID.randomUUID();
        final OrderProjectionMessage message = message(orderId);

        orderProjectionService.upsert(message);
        orderProjectionService.upsert(message);

        assertThat(orderViewRepository.findById(orderId.toString())).isPresent();
    }

    @Test
    void deleteById_ShouldMakeViewUnreachable_ThroughRealMongo() {
        final UUID orderId = UUID.randomUUID();
        orderProjectionService.upsert(message(orderId));

        orderProjectionService.deleteById(orderId);

        assertThatThrownBy(() -> orderProjectionService.findById(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upsert_ShouldNotResurrectView_WhenItArrivesAfterDelete() {
        final UUID orderId = UUID.randomUUID();
        orderProjectionService.upsert(message(orderId));
        orderProjectionService.deleteById(orderId);

        orderProjectionService.upsert(message(orderId));

        assertThatThrownBy(() -> orderProjectionService.findById(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * {@link OrderProjectionRabbitListener} and {@link OrderDeletionRabbitListener} run on two
     * independent consumer threads with no ordering guarantee between them (see
     * {@code OrderProjectionConfig}) - a delete for an order can race a concurrent upsert for the
     * same id on genuinely separate threads, not just arrive after it as the sequential test above
     * covers. Fires both operations from a {@link CyclicBarrier} so they start as close to
     * simultaneously as possible, repeated across many fresh ids to exercise both interleavings
     * (upsert's Mongo command landing before or after delete's), and asserts the one invariant that
     * must hold regardless of which thread the server processes first: a tombstoned order is never
     * resurrected.
     */
    @Test
    void upsert_ShouldNeverResurrectView_WhenRacingConcurrentlyWithDelete() throws Exception {
        for (int i = 0; i < 30; i++) {
            final UUID orderId = UUID.randomUUID();
            final CyclicBarrier barrier = new CyclicBarrier(2);

            final Future<?> upsertRun = executor.submit(() -> {
                awaitUninterruptibly(barrier);
                orderProjectionService.upsert(message(orderId));
            });
            final Future<?> deleteRun = executor.submit(() -> {
                awaitUninterruptibly(barrier);
                orderProjectionService.deleteById(orderId);
            });
            upsertRun.get(5, TimeUnit.SECONDS);
            deleteRun.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> orderProjectionService.findById(orderId))
                    .as("iteration %d: a delete racing a concurrent upsert must never leave a live (non-tombstoned) view",
                            i)
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private static void awaitUninterruptibly(final CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
