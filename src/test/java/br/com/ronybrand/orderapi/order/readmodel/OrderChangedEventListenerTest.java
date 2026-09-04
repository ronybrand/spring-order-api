package br.com.ronybrand.orderapi.order.readmodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.order.Item;
import br.com.ronybrand.orderapi.order.Order;
import br.com.ronybrand.orderapi.order.OrderChangedEvent;
import br.com.ronybrand.orderapi.order.OrderRepository;
import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessResourceFailureException;

class OrderChangedEventListenerTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final OrderChangedEventListener listener = new OrderChangedEventListener(orderRepository, rabbitTemplate);

    private static Order orderWithItems() {
        final Customer customer = Customer.builder().id(UUID.randomUUID()).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        final Order order = Order.builder().id(UUID.randomUUID()).customer(customer).status(OrderStatus.OPEN)
                .total(new BigDecimal("20.00")).updatedAt(LocalDateTime.now(ZoneOffset.UTC)).build();
        final Item item = Item.builder().id(UUID.randomUUID()).order(order).description("Widget")
                .unitPrice(new BigDecimal("10.00")).quantity(2).build();
        order.getItems().add(item);
        return order;
    }

    @Test
    void onOrderChanged_ShouldPublishProjectionMessage_WhenOrderExists() {
        final Order order = orderWithItems();
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));

        listener.onOrderChanged(new OrderChangedEvent(order.getId()));

        verify(rabbitTemplate).convertAndSend(eq(OrderProjectionConfig.EXCHANGE), eq(OrderProjectionConfig.ROUTING_KEY),
                any(OrderProjectionMessage.class));
    }

    @Test
    void onOrderChanged_ShouldSkip_WhenOrderNotFound() {
        final UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.empty());

        listener.onOrderChanged(new OrderChangedEvent(orderId));

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void onOrderChanged_ShouldNotPropagate_WhenPublishFails() {
        final Order order = orderWithItems();
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));
        doThrow(new IllegalStateException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        listener.onOrderChanged(new OrderChangedEvent(order.getId()));
    }

    @Test
    void onOrderChanged_ShouldNotPropagate_WhenRepositoryLookupFails() {
        final UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithItems(orderId)).thenThrow(new DataAccessResourceFailureException("db down"));

        listener.onOrderChanged(new OrderChangedEvent(orderId));

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }
}
