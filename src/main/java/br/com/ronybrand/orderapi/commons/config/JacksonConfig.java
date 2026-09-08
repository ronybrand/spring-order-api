package br.com.ronybrand.orderapi.commons.config;

import br.com.ronybrand.orderapi.commons.security.SensitiveFieldsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the sensitive-data masking module on the {@code ObjectMapper} auto-configured by
 * Spring Boot (any {@code com.fasterxml.jackson.databind.Module} bean is auto-detected and
 * registered).
 */
@Configuration
public class JacksonConfig {

    @Bean
    SensitiveFieldsModule sensitiveFieldsModule() {
        return new SensitiveFieldsModule();
    }

    /**
     * {@code commons}-owned, so {@code OutboxService} (which serializes every outbox payload -
     * {@code OrderChangedEvent}/{@code OrderDeletedEvent}/{@code OrderStatusChangedEvent} alike,
     * not just the notification flow's) has a mapper it actually owns, instead of reaching into
     * {@code notification.RabbitMQConfig#orderStatusObjectMapper} - a bean that config class
     * explicitly documents as free to diverge for notification-only reasons in the future, which
     * would then silently change projection/deletion payload serialization too.
     */
    @Bean
    ObjectMapper outboxObjectMapper() {
        return RawJsonObjectMapperFactory.create();
    }
}
