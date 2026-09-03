package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ronybrand.orderapi.commons.config.PaginationProperties;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.customer.CustomerRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    @SuppressWarnings("unchecked")
    private final AuditorAware<String> auditorAware = mock(AuditorAware.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PaginationProperties paginationProperties = new PaginationProperties(0, 20, 100);
    private final OrderService service =
            new OrderService(orderRepository, customerRepository, auditorAware, eventPublisher, entityManager, paginationProperties);

    private static ItemRequestDto item(final String description, final String unitPrice, final int quantity) {
        return new ItemRequestDto(description, new BigDecimal(unitPrice), quantity);
    }

    @Test
    void create_ShouldPersistOrderWithCalculatedTotal_WhenDataIsValid() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findByIdForShare(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of(item("Widget", "10.00", 3), item("Gadget", "5.50", 2)));

        assertThat(result.total()).isEqualByComparingTo("41.00");
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void create_ShouldThrowInvalidInputException_WhenCustomerIdDoesNotExist() {
        final UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdForShare(customerId)).thenReturn(Optional.empty());
        final List<ItemRequestDto> noItems = List.of();

        assertThatThrownBy(() -> service.create(customerId, noItems))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_INVALID_CUSTOMER_ID));
    }

    @Test
    void create_ShouldDefaultStatusToOpen() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findByIdForShare(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of());

        assertThat(result.status()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void create_ShouldSucceed_WhenItemListIsEmpty() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findByIdForShare(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of());

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void create_ShouldLinkEachItemToTheOrder() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findByIdForShare(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.create(customerId, List.of(item("Widget", "10.00", 1)));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().subtotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void create_ShouldPublishOrderChangedEvent() {
        final UUID customerId = UUID.randomUUID();
        final Customer customer = Customer.builder().id(customerId).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        when(customerRepository.findByIdForShare(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(customerId, List.of());

        verify(eventPublisher).publishEvent(any(OrderChangedEvent.class));
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

    private static Order openOrderWith(final Item... items) {
        final Customer customer = Customer.builder().id(UUID.randomUUID()).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        final Order order = Order.builder().id(UUID.randomUUID()).customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build();
        for (final Item existingItem : items) {
            existingItem.setOrder(order);
            order.getItems().add(existingItem);
        }
        order.calculateTotal();
        return order;
    }

    @Test
    void addItem_ShouldRecalculateTotal_WhenSucceeds() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.addItem(order.getId(), item("Widget", "10.00", 2));

        assertThat(result.total()).isEqualByComparingTo("20.00");
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void addItem_ShouldPublishOrderChangedEvent_WhenSucceeds() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addItem(order.getId(), item("Widget", "10.00", 1));

        verify(eventPublisher).publishEvent(any(OrderChangedEvent.class));
    }

    @Test
    void addItem_ShouldThrowInvalidInputException_WhenOrderIsNotOpen() {
        final Order order = openOrderWith();
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();
        final ItemRequestDto newItem = item("Widget", "10.00", 1);

        assertThatThrownBy(() -> service.addItem(orderId, newItem))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ORDER_NOT_EDITABLE));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateItemQuantity_ShouldRecalculateTotal_WhenSucceeds() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.updateItemQuantity(order.getId(), existingItem.getId(), 5);

        assertThat(result.total()).isEqualByComparingTo("50.00");
    }

    @Test
    void updateItemQuantity_ShouldPublishOrderChangedEvent_WhenSucceeds() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateItemQuantity(order.getId(), existingItem.getId(), 5);

        verify(eventPublisher).publishEvent(any(OrderChangedEvent.class));
    }

    @Test
    void updateItemQuantity_ShouldThrowInvalidInputException_WhenOrderIsCanceled() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();
        final UUID itemId = existingItem.getId();

        assertThatThrownBy(() -> service.updateItemQuantity(orderId, itemId, 5))
                .isInstanceOf(InvalidInputException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateItemQuantity_ShouldThrowResourceNotFoundException_WhenItemDoesNotBelongToOrder() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();
        final UUID unknownItemId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateItemQuantity(orderId, unknownItemId, 5))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItem_ShouldRecalculateTotal_WhenSucceeds() {
        final Item toRemove = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Item toKeep = Item.builder().id(UUID.randomUUID()).description("Gadget").unitPrice(new BigDecimal("5.00")).quantity(1).build();
        final Order order = openOrderWith(toRemove, toKeep);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.removeItem(order.getId(), toRemove.getId());

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualByComparingTo("5.00");
    }

    @Test
    void removeItem_ShouldPublishOrderChangedEvent_WhenSucceeds() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.removeItem(order.getId(), existingItem.getId());

        verify(eventPublisher).publishEvent(any(OrderChangedEvent.class));
    }

    @Test
    void removeItem_ShouldThrowInvalidInputException_WhenOrderIsNotOpen() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();
        final UUID itemId = existingItem.getId();

        assertThatThrownBy(() -> service.removeItem(orderId, itemId))
                .isInstanceOf(InvalidInputException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirm_ShouldChangeStatusToConfirmed_WhenOpenAndHasItems() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.confirm(order.getId());

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_ShouldThrowInvalidInputException_WhenAlreadyConfirmed() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();

        assertThatThrownBy(() -> service.confirm(orderId))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ORDER_INVALID_STATUS_TRANSITION));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirm_ShouldThrowInvalidInputException_WhenAlreadyCanceled() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();

        assertThatThrownBy(() -> service.confirm(orderId)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void confirm_ShouldThrowInvalidInputException_WhenNoItems() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();

        assertThatThrownBy(() -> service.confirm(orderId))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ORDER_EMPTY));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirm_ShouldPublishStatusChangedEvent_WhenCustomerHasEmail() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.confirm(order.getId());

        final ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().newStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(captor.getValue().oldStatus()).isEqualTo(OrderStatus.OPEN);
        verify(eventPublisher).publishEvent(any(OrderChangedEvent.class));
    }

    @Test
    void confirm_ShouldNotPublishStatusChangedEvent_ButShouldPublishOrderChangedEvent_WhenCustomerEmailIsBlank() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        order.getCustomer().setEmail("");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.confirm(order.getId());

        verify(eventPublisher, never()).publishEvent(any(OrderStatusChangedEvent.class));
        verify(eventPublisher).publishEvent(any(OrderChangedEvent.class));
    }

    @Test
    void cancel_ShouldChangeStatusToCanceled_WhenOpen() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.cancel(order.getId());

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cancel_ShouldChangeStatusToCanceled_WhenConfirmed() {
        final Order order = openOrderWith();
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OrderResponseDto result = service.cancel(order.getId());

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cancel_ShouldThrowInvalidInputException_WhenAlreadyCanceled() {
        final Order order = openOrderWith();
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        final UUID orderId = order.getId();

        assertThatThrownBy(() -> service.cancel(orderId))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ORDER_INVALID_STATUS_TRANSITION));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancel_ShouldPublishStatusChangedEvent_WhenCustomerHasEmail() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancel(order.getId());

        final ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().newStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(eventPublisher).publishEvent(any(OrderChangedEvent.class));
    }

    @Test
    void addItem_ShouldPropagateOptimisticLockingFailure_WhenOrderWasModifiedConcurrently() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getId()));
        final UUID orderId = order.getId();
        final ItemRequestDto newItem = item("Widget", "10.00", 1);

        assertThatThrownBy(() -> service.addItem(orderId, newItem))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void updateItemQuantity_ShouldPropagateOptimisticLockingFailure_WhenOrderWasModifiedConcurrently() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getId()));
        final UUID orderId = order.getId();
        final UUID itemId = existingItem.getId();

        assertThatThrownBy(() -> service.updateItemQuantity(orderId, itemId, 5))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void removeItem_ShouldPropagateOptimisticLockingFailure_WhenOrderWasModifiedConcurrently() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getId()));
        final UUID orderId = order.getId();
        final UUID itemId = existingItem.getId();

        assertThatThrownBy(() -> service.removeItem(orderId, itemId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void confirm_ShouldPropagateOptimisticLockingFailure_AndNotPublishEvent_WhenOrderWasModifiedConcurrently() {
        final Item existingItem = Item.builder().id(UUID.randomUUID()).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build();
        final Order order = openOrderWith(existingItem);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getId()));
        final UUID orderId = order.getId();

        assertThatThrownBy(() -> service.confirm(orderId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancel_ShouldPropagateOptimisticLockingFailure_AndNotPublishEvent_WhenOrderWasModifiedConcurrently() {
        final Order order = openOrderWith();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getId()));
        final UUID orderId = order.getId();

        assertThatThrownBy(() -> service.cancel(orderId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
