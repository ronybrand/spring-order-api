package br.com.ronybrand.orderapi.commons.messaging;

import java.util.UUID;

/**
 * Shared {@link OutboxEvent} defaults for {@link OutboxPublisherTest} and
 * {@link OutboxEventRepositoryIT}: both independently built near-identical fixtures
 * ({@code eventType("OrderChangedEvent")}, {@code exchangeName("orders.exchange")},
 * {@code payload("{}")}) via their own private factory methods - a schema/field change to
 * {@link OutboxEvent} needed updating every one of them by hand. Returns a builder, not a built
 * instance, so each call site still sets only the fields its own test actually cares about
 * (status, attempts, timestamps) on top of these shared defaults.
 */
final class OutboxEventFixtures {

    static final String EVENT_TYPE = "OrderChangedEvent";
    static final String EXCHANGE = "orders.exchange";
    static final String ROUTING_KEY = "orders.changed";

    private OutboxEventFixtures() {
    }

    static OutboxEvent.OutboxEventBuilder builder() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(EVENT_TYPE)
                .aggregateId(UUID.randomUUID())
                .exchangeName(EXCHANGE)
                .routingKey(ROUTING_KEY)
                .payload("{}")
                .attempts(0);
    }
}
