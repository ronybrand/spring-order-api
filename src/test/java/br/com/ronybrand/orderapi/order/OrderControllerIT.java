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

    @Autowired
    private ItemTestCleanupRepository itemTestCleanupRepository;

    @Autowired
    private OrderTestCleanupRepository orderTestCleanupRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        itemTestCleanupRepository.deleteAllHard();
        orderTestCleanupRepository.deleteAllHard();
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

    @Test
    void findById_ShouldReturn200_WhenExists() {
        final Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId(), HttpMethod.GET,
                request(authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(order.getId());
    }

    @Test
    void findById_ShouldReturn404_WhenNotExists() {
        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + UUID.randomUUID(), HttpMethod.GET,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND_ORDER.getCode());
    }

    @Test
    void delete_ShouldReturn204AndExcludeFromSubsequentFindById() {
        final Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());

        final ResponseEntity<Void> deleteResponse = restTemplate.exchange("/orders/" + order.getId(), HttpMethod.DELETE,
                request(authHeadersForUser()), Void.class);
        final ResponseEntity<ErrorResponseDto> findResponse = restTemplate.exchange("/orders/" + order.getId(), HttpMethod.GET,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(findResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_ShouldReturn404_WhenNotExists() {
        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + UUID.randomUUID(), HttpMethod.DELETE,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void addItem_ShouldReturn201AndRecalculateTotal_WhenOrderIsOpen() {
        final Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());
        final ItemRequestDto request = new ItemRequestDto("Widget", new BigDecimal("10.00"), 2);

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items", HttpMethod.POST,
                request(request, authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().total()).isEqualByComparingTo("20.00");
    }

    @Test
    void addItem_ShouldReturn400_WhenOrderIsNotOpen() {
        final Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.CONFIRMED).total(BigDecimal.ZERO).build());
        final ItemRequestDto request = new ItemRequestDto("Widget", new BigDecimal("10.00"), 2);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items", HttpMethod.POST,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ORDER_NOT_EDITABLE.getCode());
    }

    @Test
    void updateItemQuantity_ShouldReturn200AndRecalculateTotal_WhenOrderIsOpen() {
        Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());
        order.getItems().add(Item.builder().order(order).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build());
        order.calculateTotal();
        order = orderRepository.save(order);
        final UUID itemId = order.getItems().getFirst().getId();
        final ItemQuantityUpdateRequestDto request = new ItemQuantityUpdateRequestDto(4);

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items/" + itemId,
                HttpMethod.PATCH, request(request, authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().total()).isEqualByComparingTo("40.00");
    }

    @Test
    void updateItemQuantity_ShouldReturn404_WhenItemDoesNotBelongToOrder() {
        final Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());
        final ItemQuantityUpdateRequestDto request = new ItemQuantityUpdateRequestDto(4);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange(
                "/orders/" + order.getId() + "/items/" + UUID.randomUUID(), HttpMethod.PATCH,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND_ITEM.getCode());
    }

    @Test
    void removeItem_ShouldReturn200AndRecalculateTotal_WhenOrderIsOpen() {
        Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());
        order.getItems().add(Item.builder().order(order).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build());
        order.calculateTotal();
        order = orderRepository.save(order);
        final UUID itemId = order.getItems().getFirst().getId();

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items/" + itemId,
                HttpMethod.DELETE, request(authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void removeItem_ShouldReturn400_WhenOrderIsNotOpen() {
        Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());
        order.getItems().add(Item.builder().order(order).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build());
        order.calculateTotal();
        order = orderRepository.save(order);
        final UUID itemId = order.getItems().getFirst().getId();
        order.setStatus(OrderStatus.CANCELED);
        orderRepository.save(order);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items/" + itemId,
                HttpMethod.DELETE, request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
