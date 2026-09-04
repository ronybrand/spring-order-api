package br.com.ronybrand.orderapi.commons.config;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the plain (Jackson 2) {@code ObjectMapper} used by manual {@code MessageListener}s
 * ({@code notification.RabbitMQConfig#orderStatusObjectMapper},
 * {@code order.readmodel.OrderProjectionConfig#orderProjectionObjectMapper}) to parse a raw AMQP
 * message body themselves, before any container-level conversion could throw first. Each listener
 * still gets its own {@code @Bean} instance in its own config class - deliberately not a single
 * shared bean, so each consumer's config has no unrelated reason to change when another consumer's
 * config changes - but the construction logic itself lives in one place, so a future customization
 * (e.g. a module registration) doesn't need to be copied into every config class by hand.
 */
public final class RawJsonObjectMapperFactory {

    private RawJsonObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
