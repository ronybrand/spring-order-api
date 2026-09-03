package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AMQP payload published by {@code OrderChangedEventListener} onto the projection queue and
 * consumed by {@code OrderProjectionRabbitListener} - a full snapshot of the order at the time it
 * changed, not an incremental delta, so the consumer can always upsert idempotently.
 */
public record OrderProjectionMessage(UUID orderId, UUID customerId, OrderStatus status,
        List<OrderProjectionItem> items, BigDecimal totalAmount, LocalDateTime updatedAt) {
}
