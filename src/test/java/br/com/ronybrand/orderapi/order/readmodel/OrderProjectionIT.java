package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
}
