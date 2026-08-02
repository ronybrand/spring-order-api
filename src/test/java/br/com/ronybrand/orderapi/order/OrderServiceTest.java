package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.customer.CustomerRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;

class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    @SuppressWarnings("unchecked")
    private final AuditorAware<String> auditorAware = mock(AuditorAware.class);
    private final OrderService service = new OrderService(orderRepository, customerRepository, auditorAware);

    private static ItemRequestDto item(final String description, final String unitPrice, final int quantity) {
        return new ItemRequestDto(description, new BigDecimal(unitPrice), quantity);
    }

    @Test
    void create_ShouldPersistOrderWithCalculatedTotal_WhenDataIsValid() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of(item("Widget", "10.00", 3), item("Gadget", "5.50", 2)));

        assertThat(result.total()).isEqualByComparingTo("41.00");
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void create_ShouldThrowInvalidInputException_WhenCustomerIdDoesNotExist() {
        final UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(customerId, List.of()))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_INVALID_CUSTOMER_ID));
    }

    @Test
    void create_ShouldDefaultStatusToOpen() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of());

        assertThat(result.status()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void create_ShouldSucceed_WhenItemListIsEmpty() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of());

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void create_ShouldLinkEachItemToTheOrder() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of(item("Widget", "10.00", 1)));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().subtotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void findById_ShouldReturnOrderResponseDto_WhenExists() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        final UUID orderId = UUID.randomUUID();
        final Order order = Order.builder().id(orderId).customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        final OrderResponseDto result = service.findById(orderId);

        assertThat(result.id()).isEqualTo(orderId);
    }

    @Test
    void findById_ShouldThrowResourceNotFoundException_WhenNotExists() {
        final UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_ShouldSoftDeleteOrder_WhenExists() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        final UUID orderId = UUID.randomUUID();
        final Order order = Order.builder().id(orderId).customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("some-user"));

        service.delete(orderId);

        assertThat(order.getDeletedAt()).isNotNull();
        assertThat(order.getDeletedBy()).isEqualTo("some-user");
        verify(orderRepository).save(order);
    }

    @Test
    void delete_ShouldThrowResourceNotFoundException_WhenNotExists() {
        final UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(orderId)).isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }
}
