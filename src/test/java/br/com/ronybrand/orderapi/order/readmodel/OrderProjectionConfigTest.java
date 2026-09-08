package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Same rationale as {@code notification.RabbitMQConfigTest}: a single stuck message retrying
 * blocks whichever consumer thread handles it, and with a single consumer that stalls every other
 * queued message behind it. Both the upsert and delete queues get the same concurrency here.
 */
class OrderProjectionConfigTest {

    private final OrderProjectionConfig config = new OrderProjectionConfig();

    @Test
    void orderProjectionContainer_ShouldUseTheConfiguredConcurrency() {
        final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        final OrderProjectionRabbitListener listener = mock(OrderProjectionRabbitListener.class);

        final SimpleMessageListenerContainer container =
                config.orderProjectionContainer(connectionFactory, listener, 4);

        assertThat((int) ReflectionTestUtils.getField(container, "concurrentConsumers")).isEqualTo(4);
    }

    @Test
    void orderProjectionDeleteContainer_ShouldUseTheConfiguredConcurrency() {
        final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        final OrderDeletionRabbitListener listener = mock(OrderDeletionRabbitListener.class);

        final SimpleMessageListenerContainer container =
                config.orderProjectionDeleteContainer(connectionFactory, listener, 4);

        assertThat((int) ReflectionTestUtils.getField(container, "concurrentConsumers")).isEqualTo(4);
    }
}
