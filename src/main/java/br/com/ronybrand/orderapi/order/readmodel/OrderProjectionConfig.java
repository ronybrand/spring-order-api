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
 * Topology for the order-views projection: an exchange isolated from {@code notification.RabbitMQConfig}
 * (which is semantically coupled to the email flow, a different consumer purpose) - but shared
 * between the upsert and delete event types, since both serve the same purpose (keeping
 * {@code order_views} in sync), each with its own routing key/queue/DLQ.
 */
@Configuration
public class OrderProjectionConfig {

    public static final String EXCHANGE = "order.projection.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "order.projection.exchange.dlx";
    public static final String ROUTING_KEY = "order.changed";
    public static final String QUEUE = "order.projection.queue";
    public static final String DEAD_LETTER_QUEUE = "order.projection.dlq";
    public static final String DELETE_ROUTING_KEY = "order.deleted";
    public static final String DELETE_QUEUE = "order.projection.delete.queue";
    public static final String DELETE_DEAD_LETTER_QUEUE = "order.projection.delete.dlq";

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

    @Bean
    Queue orderProjectionDeleteQueue() {
        return QueueBuilder.durable(DELETE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DELETE_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue orderProjectionDeleteDeadLetterQueue() {
        return QueueBuilder.durable(DELETE_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding orderProjectionDeleteBinding() {
        return BindingBuilder.bind(orderProjectionDeleteQueue()).to(orderProjectionExchange()).with(DELETE_ROUTING_KEY);
    }

    @Bean
    Binding orderProjectionDeleteDeadLetterBinding() {
        return BindingBuilder.bind(orderProjectionDeleteDeadLetterQueue()).to(orderProjectionDeadLetterExchange()).with(DELETE_ROUTING_KEY);
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

    @Bean
    SimpleMessageListenerContainer orderProjectionDeleteContainer(final ConnectionFactory connectionFactory,
            final OrderDeletionRabbitListener listener) {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(DELETE_QUEUE);
        container.setMessageListener(listener);
        container.setDefaultRequeueRejected(false);
        return container;
    }
}
