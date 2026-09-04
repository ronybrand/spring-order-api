package br.com.ronybrand.orderapi.order;

import java.util.UUID;

/**
 * Published at the end of {@link OrderService#delete} - the counterpart to {@link OrderChangedEvent}
 * for the one mutation that has no snapshot to carry: the order is gone, not changed, so the id
 * alone is the whole message, unlike {@link OrderChangedEvent#from}.
 */
public record OrderDeletedEvent(UUID orderId) {
}
