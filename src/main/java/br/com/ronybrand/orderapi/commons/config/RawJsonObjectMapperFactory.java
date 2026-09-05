package br.com.ronybrand.orderapi.commons.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Builds the plain (Jackson 2) {@code ObjectMapper} used by manual {@code MessageListener}s
 * ({@code notification.RabbitMQConfig#orderStatusObjectMapper},
 * {@code order.readmodel.OrderProjectionConfig#orderProjectionObjectMapper}) to parse a raw AMQP
 * message body themselves, before any container-level conversion could throw first, and by
 * {@code OutboxService} to build the payload every outbox row ships to RabbitMQ as-is (see ADR
 * 0006 - {@code OutboxPublisher} sends that payload verbatim, it never runs it through
 * {@code RabbitTemplate}'s configured {@code JacksonJsonMessageConverter}). Each listener still
 * gets its own {@code @Bean} instance in its own config class - deliberately not a single shared
 * bean, so each consumer's config has no unrelated reason to change when another consumer's config
 * changes - but the construction logic itself lives in one place, so a future customization (e.g. a
 * module registration) doesn't need to be copied into every config class by hand.
 *
 * <p>{@code WRITE_DATES_AS_TIMESTAMPS} is explicitly disabled: {@code findAndRegisterModules()}
 * alone leaves it enabled, which would serialize every {@code LocalDateTime} field as a numeric
 * array instead of the ISO-8601 string the message contracts (see the Pact tests in
 * {@code notification}/{@code order.readmodel}) actually document and other services depend on.
 */
public final class RawJsonObjectMapperFactory {

    private RawJsonObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        return new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
