package br.com.ronybrand.orderapi.order.readmodel;

import java.util.UUID;

/**
 * AMQP payload written to the outbox for the delete queue (see
 * {@code br.com.ronybrand.orderapi.commons.messaging.OutboxService}) and consumed by
 * {@link OrderDeletionRabbitListener} - unlike {@link OrderProjectionMessage} there's no order
 * snapshot to carry, the order is soft-deleted and hidden from every query by {@code @SQLRestriction}.
 */
public record OrderDeletionMessage(UUID orderId) {
}
