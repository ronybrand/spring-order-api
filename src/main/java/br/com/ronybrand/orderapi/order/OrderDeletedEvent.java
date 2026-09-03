package br.com.ronybrand.orderapi.order;

import java.util.UUID;

/**
 * Published at the end of {@link OrderService#delete} - the counterpart to {@link OrderChangedEvent}
 * for the one mutation that {@code @SQLRestriction} on {@link Order} hides from every other query,
 * including {@link OrderRepository#findByIdWithItems}, so this can't be folded into
 * {@link OrderChangedEvent} and re-fetched the same way.
 */
public record OrderDeletedEvent(UUID orderId) {
}
