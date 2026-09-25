package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A single stuck message retrying (up to {@code NotificationRetryPolicy.MAX_RETRIES} backoff
 * steps) blocks whichever consumer thread is handling it - with a single consumer, every other
 * already-queued message stalls behind it for the same duration. Concurrency &gt; 1 bounds that
 * blast radius to one thread instead of the whole queue.
 */
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig(RabbitMQConfig.QUEUE, RabbitMQConfig.DEAD_LETTER_QUEUE);

    @Test
    void orderNotificationContainer_ShouldUseTheConfiguredConcurrency() {
        final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        final OrderNotificationRabbitListener listener = mock(OrderNotificationRabbitListener.class);

        final SimpleMessageListenerContainer container =
                config.orderNotificationContainer(connectionFactory, listener, 4);

        assertThat((int) ReflectionTestUtils.getField(container, "concurrentConsumers")).isEqualTo(4);
    }
}
