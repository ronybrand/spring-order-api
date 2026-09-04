package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.order.Item;
import br.com.ronybrand.orderapi.order.Order;
import br.com.ronybrand.orderapi.order.OrderChangedEvent;
import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OrderChangedEventListenerTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final OrderChangedEventListener listener = new OrderChangedEventListener(rabbitTemplate);

    private static OrderChangedEvent eventForOrderWithItems() {
        final Customer customer = Customer.builder().id(UUID.randomUUID()).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        final Order order = Order.builder().id(UUID.randomUUID()).customer(customer).status(OrderStatus.OPEN)
                .total(new BigDecimal("20.00")).updatedAt(LocalDateTime.now(ZoneOffset.UTC)).build();
        final Item item = Item.builder().id(UUID.randomUUID()).order(order).description("Widget")
                .unitPrice(new BigDecimal("10.00")).quantity(2).build();
        order.getItems().add(item);
        return OrderChangedEvent.from(order);
    }

    @Test
    void onOrderChanged_ShouldPublishProjectionMessage() {
        final OrderChangedEvent event = eventForOrderWithItems();

        listener.onOrderChanged(event);

        verify(rabbitTemplate).convertAndSend(eq(OrderProjectionConfig.EXCHANGE), eq(OrderProjectionConfig.ROUTING_KEY),
                any(OrderProjectionMessage.class));
    }

    @Test
    void onOrderChanged_ShouldMapEventFieldsOntoProjectionMessage() {
        final OrderChangedEvent event = eventForOrderWithItems();
        final ArgumentCaptor<OrderProjectionMessage> captor = ArgumentCaptor.forClass(OrderProjectionMessage.class);

        listener.onOrderChanged(event);

        verify(rabbitTemplate).convertAndSend(eq(OrderProjectionConfig.EXCHANGE), eq(OrderProjectionConfig.ROUTING_KEY), captor.capture());
        final OrderProjectionMessage message = captor.getValue();
        assertThat(message.orderId()).isEqualTo(event.orderId());
        assertThat(message.customerId()).isEqualTo(event.customerId());
        assertThat(message.status()).isEqualTo(event.status());
        assertThat(message.items()).hasSize(1);
        assertThat(message.items().getFirst().description()).isEqualTo("Widget");
    }

    @Test
    void onOrderChanged_ShouldNotPropagate_WhenPublishFails() {
        final OrderChangedEvent event = eventForOrderWithItems();
        doThrow(new IllegalStateException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        listener.onOrderChanged(event);
    }
}
