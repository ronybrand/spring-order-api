package br.com.ronybrand.orderapi.order.readmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;
import org.bson.UuidRepresentation;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology for the order-changed projection: an exchange/queue isolated from
 * {@code notification.RabbitMQConfig} (which is semantically coupled to the email flow) - a
 * separate consumer-purpose gets a separate exchange, not a shared one with a second routing key.
 */
@Configuration
public class OrderProjectionConfig {

    public static final String EXCHANGE = "order.projection.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "order.projection.exchange.dlx";
    public static final String ROUTING_KEY = "order.changed";
    public static final String QUEUE = "order.projection.queue";
    public static final String DEAD_LETTER_QUEUE = "order.projection.dlq";

    @Bean
    DirectExchange orderProjectionExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange orderProjectionDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderProjectionQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    Queue orderProjectionDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding orderProjectionBinding() {
        return BindingBuilder.bind(orderProjectionQueue()).to(orderProjectionExchange()).with(ROUTING_KEY);
    }

    @Bean
    Binding orderProjectionDeadLetterBinding() {
        return BindingBuilder.bind(orderProjectionDeadLetterQueue()).to(orderProjectionDeadLetterExchange()).with(ROUTING_KEY);
    }

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

    /**
     * A dedicated, explicit {@code ObjectMapper} for {@link OrderProjectionRabbitListener} to parse
     * the raw message body itself - deliberately duplicated from
     * {@code notification.RabbitMQConfig#orderStatusObjectMapper} rather than reused/renamed,
     * to honor keeping that class untouched (its exchange is semantically coupled to the email
     * flow and shouldn't gain a second, unrelated reason to change).
     */
    @Bean
    ObjectMapper orderProjectionObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    SimpleMessageListenerContainer orderProjectionContainer(final ConnectionFactory connectionFactory,
            final OrderProjectionRabbitListener listener) {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(QUEUE);
        container.setMessageListener(listener);
        // Same safety net as notification.RabbitMQConfig#orderNotificationContainer: any exception
        // escaping the listener's own classification must still go to the DLQ, not requeue forever.
        container.setDefaultRequeueRejected(false);
        return container;
    }
}
