package br.com.ronybrand.orderapi.order;

import java.util.UUID;

/**
 * Published, unconditionally, at the end of every mutating {@link OrderService} method except
 * {@code delete} (see {@link OrderDeletedEvent}, its counterpart for that path). Carries only the
 * id: a technical "this order changed" signal, distinct from {@link OrderStatusChangedEvent} (a
 * business event gated by customer email, feeding the notification flow only).
 */
public record OrderChangedEvent(UUID orderId) {
}
