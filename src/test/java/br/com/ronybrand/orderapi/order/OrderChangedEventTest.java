package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.customer.Customer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderChangedEventTest {

    @Test
    void from_ShouldCaptureFullSnapshot_IncludingItems() {
        final Customer customer = Customer.builder().id(UUID.randomUUID()).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        final LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        final Order order = Order.builder().id(UUID.randomUUID()).customer(customer).status(OrderStatus.CONFIRMED)
                .total(new BigDecimal("20.00")).updatedAt(updatedAt).build();
        final Item item = Item.builder().id(UUID.randomUUID()).order(order).description("Widget")
                .unitPrice(new BigDecimal("10.00")).quantity(2).build();
        order.getItems().add(item);

        final OrderChangedEvent event = OrderChangedEvent.from(order);

        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.customerId()).isEqualTo(customer.getId());
        assertThat(event.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(event.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(event.updatedAt()).isEqualTo(updatedAt);
        assertThat(event.items()).hasSize(1);
        final OrderChangedEvent.ItemSnapshot snapshot = event.items().getFirst();
        assertThat(snapshot.id()).isEqualTo(item.getId());
        assertThat(snapshot.description()).isEqualTo("Widget");
        assertThat(snapshot.unitPrice()).isEqualByComparingTo("10.00");
        assertThat(snapshot.quantity()).isEqualTo(2);
        assertThat(snapshot.subtotal()).isEqualByComparingTo("20.00");
    }

    @Test
    void from_ShouldCaptureEmptyItems_WhenOrderHasNone() {
        final Customer customer = Customer.builder().id(UUID.randomUUID()).name("Ada Lovelace").taxId("TAX-1").email("ada@example.com").build();
        final Order order = Order.builder().id(UUID.randomUUID()).customer(customer).status(OrderStatus.OPEN)
                .total(BigDecimal.ZERO).updatedAt(LocalDateTime.now(ZoneOffset.UTC)).build();

        final OrderChangedEvent event = OrderChangedEvent.from(order);

        assertThat(event.items()).isEmpty();
    }
}
