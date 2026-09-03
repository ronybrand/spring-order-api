package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ErrorResponseDto;
import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class OrderViewControllerIT extends AbstractAuthIntegrationTest {

    @Autowired
    private OrderViewRepository orderViewRepository;

    @BeforeEach
    void setUp() {
        orderViewRepository.deleteAll();
    }

    private OrderView saveView(final UUID orderId) {
        final OrderViewItem item = OrderViewItem.builder().id(UUID.randomUUID()).description("Widget")
                .unitPrice(new BigDecimal("10.00")).quantity(2).subtotal(new BigDecimal("20.00")).build();
        return orderViewRepository.save(OrderView.builder()
                .id(orderId.toString())
                .customerId(UUID.randomUUID())
                .status(OrderStatus.CONFIRMED)
                .items(List.of(item))
                .totalAmount(new BigDecimal("20.00"))
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
    }

    @Test
    void findView_ShouldReturn200_WhenExists() {
        final UUID orderId = UUID.randomUUID();
        saveView(orderId);

        final ResponseEntity<OrderViewResponseDto> response = restTemplate.exchange("/orders/" + orderId + "/view",
                HttpMethod.GET, request(authHeadersForUser()), OrderViewResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().orderId()).isEqualTo(orderId);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getBody().items()).hasSize(1);
    }

    @Test
    void findView_ShouldReturn404_WhenNotExists() {
        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + UUID.randomUUID() + "/view",
                HttpMethod.GET, request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND_ORDER_VIEW.getCode());
    }

    @Test
    void findView_ShouldReturn401_WhenUnauthenticated() {
        final UUID orderId = UUID.randomUUID();
        saveView(orderId);

        final ResponseEntity<Void> response = restTemplate.exchange("/orders/" + orderId + "/view",
                HttpMethod.GET, request(headers()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
