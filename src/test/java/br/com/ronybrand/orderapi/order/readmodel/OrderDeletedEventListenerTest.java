package br.com.ronybrand.orderapi.order.readmodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.ronybrand.orderapi.order.OrderDeletedEvent;
import br.com.ronybrand.orderapi.commons.messaging.MessagingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OrderDeletedEventListenerTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        private final OrderDeletedEventListener listener =
            new OrderDeletedEventListener(rabbitTemplate, new MessagingMetrics(new SimpleMeterRegistry()));

    @Test
    void onOrderDeleted_ShouldPublishDeletionMessage() {
        final UUID orderId = UUID.randomUUID();

        listener.onOrderDeleted(new OrderDeletedEvent(orderId));

        verify(rabbitTemplate).convertAndSend(eq(OrderProjectionConfig.EXCHANGE), eq(OrderProjectionConfig.DELETE_ROUTING_KEY),
                eq(new OrderDeletionMessage(orderId)));
    }

    @Test
    void onOrderDeleted_ShouldNotPropagate_WhenPublishFails() {
        final UUID orderId = UUID.randomUUID();
        doThrow(new IllegalStateException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        listener.onOrderDeleted(new OrderDeletedEvent(orderId));
    }
}
