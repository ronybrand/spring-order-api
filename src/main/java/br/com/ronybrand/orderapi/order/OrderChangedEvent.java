package br.com.ronybrand.orderapi.order;

import java.util.UUID;

/**
 * Published, unconditionally, at the end of every mutating {@link OrderService} method
 * (create/addItem/updateItemQuantity/removeItem/confirm/cancel - not delete, soft-deleted orders
 * are not reflected in any read model yet). Carries only the id: a technical "this order changed"
 * signal, distinct from {@link OrderStatusChangedEvent} (a business event gated by customer
 * email, feeding the notification flow only).
 */
public record OrderChangedEvent(UUID orderId) {
}
