package br.com.ronybrand.orderapi.commons.config;

import br.com.ronybrand.orderapi.commons.security.SensitiveFieldsModule;
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
}
