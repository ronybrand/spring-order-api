package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ErrorResponseDto;
import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.customer.CustomerRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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
class OrderControllerIT extends AbstractAuthIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        customer = customerRepository.save(
                Customer.builder().name("Ada Lovelace")
                        .taxId("TAX-" + UUID.randomUUID().toString().substring(0, 8))
                        .email("ada@example.com")
                        .build());
    }

    @Test
    void create_ShouldReturn201WithCalculatedTotal_WhenCallerIsUser() {
        final OrderCreateRequestDto request = new OrderCreateRequestDto(customer.getId(),
                List.of(new ItemRequestDto("Widget", new BigDecimal("10.00"), 3)));

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders", HttpMethod.POST,
                request(request, authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().total()).isEqualByComparingTo("30.00");
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void create_ShouldReturn401_WhenUnauthenticated() {
        final OrderCreateRequestDto request = new OrderCreateRequestDto(customer.getId(), List.of());

        final ResponseEntity<Void> response = restTemplate.exchange("/orders", HttpMethod.POST,
                request(request, headers()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void create_ShouldReturn400_WhenCustomerIdDoesNotExist() {
        final OrderCreateRequestDto request = new OrderCreateRequestDto(UUID.randomUUID(), List.of());

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders", HttpMethod.POST,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_INVALID_CUSTOMER_ID.getCode());
    }

    @Test
    void create_ShouldReturn400_WhenItemListExceedsLimit() {
        final List<ItemRequestDto> tooManyItems = IntStream.range(0, 201)
                .mapToObj(i -> new ItemRequestDto("Item " + i, new BigDecimal("1.00"), 1))
                .toList();
        final OrderCreateRequestDto request = new OrderCreateRequestDto(customer.getId(), tooManyItems);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders", HttpMethod.POST,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_ShouldReturn400_WhenItemUnitPriceIsNotPositive() {
        final OrderCreateRequestDto request = new OrderCreateRequestDto(customer.getId(),
                List.of(new ItemRequestDto("Widget", new BigDecimal("-1.00"), 1)));

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders", HttpMethod.POST,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_ShouldSucceed_WhenItemListIsEmpty() {
        final OrderCreateRequestDto request = new OrderCreateRequestDto(customer.getId(), List.of());

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders", HttpMethod.POST,
                request(request, authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().total()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
