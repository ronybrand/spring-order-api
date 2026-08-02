package br.com.ronybrand.orderapi.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published at the end of {@code confirm()}/{@code cancel()}, only when the customer has a
 * non-blank email (DOMAIN.md §5). Carries the payload the async notification path needs, without
 * a lazy reference back to the entity.
 */
public record OrderStatusChangedEvent(UUID orderId, String customerEmail, String customerName,
        OrderStatus oldStatus, OrderStatus newStatus, BigDecimal totalAmount, LocalDateTime changedAt) {
}
