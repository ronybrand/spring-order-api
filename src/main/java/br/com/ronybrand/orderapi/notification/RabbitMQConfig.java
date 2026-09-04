package br.com.ronybrand.orderapi.notification;

import br.com.ronybrand.orderapi.commons.config.RawJsonObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology for the order-status notification: a durable exchange/queue with the queue's
 * dead-letter attributes pointing at a separate DLX/queue - a real, inspectable queue (RabbitMQ
 * Management UI), never a log or silent drop (DOMAIN.md §5, rule 3).
 *
 * <p>The consumer is a raw {@link SimpleMessageListenerContainer} wired to
 * {@link OrderNotificationRabbitListener}, a plain {@code MessageListener} - not
 * {@code @RabbitListener} - so the listener receives the unconverted body and can classify a
 * malformed payload before any conversion machinery would otherwise throw first.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "orders.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "orders.exchange.dlx";
    public static final String ROUTING_KEY = "order.status.changed";
    public static final String QUEUE = "order.status.notifications.queue";
    public static final String DEAD_LETTER_QUEUE = "order.status.notifications.dlq";

    @Bean
    DirectExchange ordersExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange ordersDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderStatusNotificationsQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    Queue orderStatusNotificationsDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding orderStatusNotificationsBinding() {
        return BindingBuilder.bind(orderStatusNotificationsQueue()).to(ordersExchange()).with(ROUTING_KEY);
    }

    @Bean
    Binding orderStatusNotificationsDeadLetterBinding() {
        return BindingBuilder.bind(orderStatusNotificationsDeadLetterQueue()).to(ordersDeadLetterExchange()).with(ROUTING_KEY);
    }

    @Bean
    JacksonJsonMessageConverter amqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * A dedicated, explicit {@code ObjectMapper} for {@link OrderNotificationRabbitListener} to
     * parse the raw message body itself - Spring Boot does not otherwise expose a classic Jackson
     * 2 {@code ObjectMapper} bean here. Its own {@code @Bean} instance, not shared with
     * {@code order.readmodel.OrderProjectionConfig}'s equivalent, so this config has no unrelated
     * reason to change when the read-model's does; {@link RawJsonObjectMapperFactory} centralizes
     * just the construction logic both share.
     */
    @Bean
    ObjectMapper orderStatusObjectMapper() {
        return RawJsonObjectMapperFactory.create();
    }

    @Bean
    RabbitTemplate rabbitTemplate(final ConnectionFactory connectionFactory, final JacksonJsonMessageConverter converter) {
        final RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    SimpleMessageListenerContainer orderNotificationContainer(final ConnectionFactory connectionFactory,
            final OrderNotificationRabbitListener listener) {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(QUEUE);
        container.setMessageListener(listener);
        // Safety net: the listener always classifies its own exceptions into
        // AmqpRejectAndDontRequeueException (retry-exhausted/malformed) or lets EmailSendingException
        // retry in-process. Any *other* exception escaping it (e.g. an unclassified bug) must still
        // go to the DLQ, not requeue forever on the same message (DOMAIN.md §5).
        container.setDefaultRequeueRejected(false);
        return container;
    }
}
