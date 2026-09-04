package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.commons.config.RawJsonObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;
import org.bson.UuidRepresentation;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageListener;
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
        return buildQueue(QUEUE, ROUTING_KEY);
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
        return buildQueue(DELETE_QUEUE, DELETE_ROUTING_KEY);
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
     * Shared by {@link #orderProjectionQueue()} and {@link #orderProjectionDeleteQueue()} - both
     * point their dead letters at the same {@link #DEAD_LETTER_EXCHANGE}, keyed by their own
     * routing key so upsert and delete failures land in separate, independently inspectable DLQs.
     */
    private Queue buildQueue(final String name, final String routingKey) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    /**
     * The MongoDB Java driver refuses to encode {@code UUID} fields (like {@link OrderView#getId()}
     * and {@link OrderView#getCustomerId()}) unless a representation is chosen explicitly - there's
     * no safe default across legacy/standard encodings. {@code spring.mongodb.representation.uuid}
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
     * the raw message body itself - its own {@code @Bean} instance, not shared with
     * {@code notification.RabbitMQConfig}'s equivalent, so that class has no unrelated reason to
     * change when this one does; {@link RawJsonObjectMapperFactory} centralizes just the
     * construction logic both share.
     */
    @Bean
    ObjectMapper orderProjectionObjectMapper() {
        return RawJsonObjectMapperFactory.create();
    }

    @Bean
    SimpleMessageListenerContainer orderProjectionContainer(final ConnectionFactory connectionFactory,
            final OrderProjectionRabbitListener listener) {
        return buildContainer(connectionFactory, QUEUE, listener);
    }

    @Bean
    SimpleMessageListenerContainer orderProjectionDeleteContainer(final ConnectionFactory connectionFactory,
            final OrderDeletionRabbitListener listener) {
        return buildContainer(connectionFactory, DELETE_QUEUE, listener);
    }

    /**
     * Shared by {@link #orderProjectionContainer} and {@link #orderProjectionDeleteContainer} - any
     * exception escaping a listener's own classification must still go to that queue's DLQ, not
     * requeue forever, same safety net as {@code notification.RabbitMQConfig#orderNotificationContainer}.
     */
    private SimpleMessageListenerContainer buildContainer(final ConnectionFactory connectionFactory,
            final String queueName, final MessageListener listener) {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(listener);
        container.setDefaultRequeueRejected(false);
        return container;
    }
}
