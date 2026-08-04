package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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

    private Order saveOrder(final OrderStatus status) {
        return orderRepository.save(Order.builder().customer(customer).status(status).total(BigDecimal.ZERO).build());
    }

    private Order saveOrderWithOneItem() {
        final Order order = saveOrder(OrderStatus.OPEN);
        order.getItems().add(Item.builder().order(order).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build());
        order.calculateTotal();
        return orderRepository.save(order);
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
        final Order order = saveOrder(OrderStatus.OPEN);

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
        final Order order = saveOrder(OrderStatus.OPEN);

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
        final Order order = saveOrder(OrderStatus.OPEN);
        final ItemRequestDto request = new ItemRequestDto("Widget", new BigDecimal("10.00"), 2);

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items", HttpMethod.POST,
                request(request, authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().total()).isEqualByComparingTo("20.00");
    }

    @Test
    void addItem_ShouldReturn400_WhenOrderIsNotOpen() {
        final Order order = saveOrder(OrderStatus.CONFIRMED);
        final ItemRequestDto request = new ItemRequestDto("Widget", new BigDecimal("10.00"), 2);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items", HttpMethod.POST,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ORDER_NOT_EDITABLE.getCode());
    }

    /**
     * Reproduces the lost-update scenario deterministically instead of racing real threads against
     * an embedded server (which would make the assertion timing-dependent): two independent reads of
     * the same row, one save commits and bumps {@code @Version}, the second save - built from the
     * now-stale read - must be rejected by Hibernate rather than silently overwriting the first
     * change (DOMAIN.md §4.10).
     */
    @Test
    void save_ShouldThrowOptimisticLockingFailure_WhenOrderWasModifiedByAnotherTransactionSinceItWasRead() {
        final Order order = saveOrder(OrderStatus.OPEN);
        final Order firstRead = orderRepository.findById(order.getId()).orElseThrow();
        final Order staleRead = orderRepository.findById(order.getId()).orElseThrow();

        firstRead.setStatus(OrderStatus.CONFIRMED);
        orderRepository.saveAndFlush(firstRead);

        staleRead.setStatus(OrderStatus.CANCELED);
        assertThatThrownBy(() -> orderRepository.saveAndFlush(staleRead))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void updateItemQuantity_ShouldReturn200AndRecalculateTotal_WhenOrderIsOpen() {
        final Order order = saveOrderWithOneItem();
        final UUID itemId = order.getItems().getFirst().getId();
        final ItemQuantityUpdateRequestDto request = new ItemQuantityUpdateRequestDto(4);

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items/" + itemId,
                HttpMethod.PATCH, request(request, authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().total()).isEqualByComparingTo("40.00");
    }

    @Test
    void updateItemQuantity_ShouldReturn404_WhenItemDoesNotBelongToOrder() {
        final Order order = saveOrder(OrderStatus.OPEN);
        final ItemQuantityUpdateRequestDto request = new ItemQuantityUpdateRequestDto(4);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange(
                "/orders/" + order.getId() + "/items/" + UUID.randomUUID(), HttpMethod.PATCH,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND_ITEM.getCode());
    }

    @Test
    void removeItem_ShouldReturn200AndRecalculateTotal_WhenOrderIsOpen() {
        final Order order = saveOrderWithOneItem();
        final UUID itemId = order.getItems().getFirst().getId();

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items/" + itemId,
                HttpMethod.DELETE, request(authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void removeItem_ShouldReturn400_WhenOrderIsNotOpen() {
        final Order order = saveOrderWithOneItem();
        final UUID itemId = order.getItems().getFirst().getId();
        order.setStatus(OrderStatus.CANCELED);
        orderRepository.save(order);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/items/" + itemId,
                HttpMethod.DELETE, request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirm_ShouldReturn200_WhenOpenAndHasItems() {
        final Order order = saveOrderWithOneItem();

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/confirm", HttpMethod.POST,
                request(authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_ShouldReturn400_WhenOrderHasNoItems() {
        final Order order = saveOrder(OrderStatus.OPEN);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/confirm", HttpMethod.POST,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ORDER_EMPTY.getCode());
    }

    @Test
    void confirm_ShouldReturn400_WhenAlreadyConfirmed() {
        final Order order = saveOrderWithOneItem();
        restTemplate.exchange("/orders/" + order.getId() + "/confirm", HttpMethod.POST, request(authHeadersForUser()), OrderResponseDto.class);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/confirm", HttpMethod.POST,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ORDER_INVALID_STATUS_TRANSITION.getCode());
    }

    @Test
    void cancel_ShouldReturn200_WhenOpen() {
        final Order order = saveOrder(OrderStatus.OPEN);

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/cancel", HttpMethod.POST,
                request(authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cancel_ShouldReturn200_WhenConfirmed() {
        final Order order = saveOrderWithOneItem();
        restTemplate.exchange("/orders/" + order.getId() + "/confirm", HttpMethod.POST, request(authHeadersForUser()), OrderResponseDto.class);

        final ResponseEntity<OrderResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/cancel", HttpMethod.POST,
                request(authHeadersForUser()), OrderResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cancel_ShouldReturn400_WhenAlreadyCanceled() {
        final Order order = saveOrder(OrderStatus.OPEN);
        restTemplate.exchange("/orders/" + order.getId() + "/cancel", HttpMethod.POST, request(authHeadersForUser()), OrderResponseDto.class);

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/" + order.getId() + "/cancel", HttpMethod.POST,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ORDER_INVALID_STATUS_TRANSITION.getCode());
    }

    @Test
    void search_ShouldReturn200WithFilteredResults() {
        saveOrder(OrderStatus.OPEN);
        saveOrder(OrderStatus.CANCELED);

        final ResponseEntity<String> response = restTemplate.exchange("/orders/search?filter[status]=OPEN", HttpMethod.GET,
                request(authHeadersForUser()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"OPEN\"").doesNotContain("\"status\":\"CANCELED\"");
    }

    @Test
    void search_ShouldReturn400_WhenSortFieldIsInvalid() {
        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/orders/search?order=notAField", HttpMethod.GET,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_INVALID_SORT_FIELD.getCode());
    }

    @Test
    void search_ShouldIgnoreFilterOnAssociationField_Silently() {
        saveOrder(OrderStatus.OPEN);

        final ResponseEntity<String> response = restTemplate.exchange("/orders/search?filter[customer]=x", HttpMethod.GET,
                request(authHeadersForUser()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
