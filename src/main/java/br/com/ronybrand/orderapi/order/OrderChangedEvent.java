package br.com.ronybrand.orderapi.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Published, unconditionally, at the end of every mutating {@link OrderService} method except
 * {@code delete} (see {@link OrderDeletedEvent}, its counterpart for that path) - a technical
 * "this order changed" signal, distinct from {@link OrderStatusChangedEvent} (a business event
 * gated by customer email, feeding the notification flow only).
 *
 * <p>Carries a full snapshot, not just the id: an earlier design re-fetched the order (fetch-joined)
 * after commit purely because the id was all a plain event carried, paying a second DB round trip
 * for data {@link OrderService} already held in memory. {@link #from} builds this snapshot from the
 * same managed {@link Order} instance the service just saved, inside the original transaction -
 * {@code OrderService} flushes right before enqueueing it to the outbox so {@code updatedAt} (a
 * {@code @LastModifiedDate}, only populated by Hibernate's auditing listener at flush time) is
 * guaranteed accurate by the time it's captured here. See
 * {@link br.com.ronybrand.orderapi.commons.messaging.OutboxService} and
 * <a href="../../../../../../../docs/adr/0006-transactional-outbox.md">ADR 0006</a> for how this
 * event actually reaches RabbitMQ - written to the {@code outbox_events} table in the same
 * transaction as the order, not published directly.
 */
public record OrderChangedEvent(UUID orderId, UUID customerId, OrderStatus status, List<OrderChangedEvent.ItemSnapshot> items,
        BigDecimal totalAmount, LocalDateTime updatedAt) {

    public record ItemSnapshot(UUID id, String description, BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {
    }

    public static OrderChangedEvent from(final Order order) {
        final List<ItemSnapshot> items = order.getItems().stream()
                .map(item -> new ItemSnapshot(item.getId(), item.getDescription(), item.getUnitPrice(), item.getQuantity(),
                        item.getSubtotal()))
                .toList();
        return new OrderChangedEvent(order.getId(), order.getCustomer().getId(), order.getStatus(), items,
                order.getTotal(), order.getUpdatedAt());
    }
}
