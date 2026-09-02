package br.com.ronybrand.orderapi.order.readmodel;

import com.mongodb.MongoClientSettings;
import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderProjectionConfig {

    /**
     * The MongoDB Java driver refuses to encode {@code UUID} fields (like {@link OrderView#getId()}
     * and {@link OrderView#getCustomerId()}) unless a representation is chosen explicitly - there's
     * no safe default across legacy/standard encodings. {@code spring.data.mongodb.uuid-representation}
     * alone does not take effect here because {@code @ServiceConnection} (Testcontainers) supplies
     * its own {@code MongoConnectionDetails} bean, which bypasses that property; a
     * {@link MongoClientSettingsBuilderCustomizer} applies regardless of where the connection
     * details came from.
     */
    @Bean
    MongoClientSettingsBuilderCustomizer uuidRepresentationCustomizer() {
        return (final MongoClientSettings.Builder builder) -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
