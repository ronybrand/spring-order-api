package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class OrderDeletionRabbitListenerTest {

    private final OrderProjectionService orderProjectionService = mock(OrderProjectionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OrderDeletionRabbitListener listener = new OrderDeletionRabbitListener(orderProjectionService, objectMapper);

    private static Message rawMessage(final byte[] body) {
        return new Message(body, new MessageProperties());
    }

    private static OrderDeletionMessage message() {
        return new OrderDeletionMessage(UUID.randomUUID());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenPayloadIsNotValidJson() {
        final Message message = rawMessage("not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(orderProjectionService, never()).deleteById(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenOrderIdIsMissing() {
        final Message message = rawMessage("{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(orderProjectionService, never()).deleteById(any());
    }

    @Test
    void onMessage_ShouldSucceed_WhenPayloadIsValidAndDeleteSucceeds() throws JsonProcessingException {
        final Message rawMessage = rawMessage(objectMapper.writeValueAsBytes(message()));

        listener.onMessage(rawMessage);

        verify(orderProjectionService, times(1)).deleteById(any());
    }

    @Test
    void onMessage_ShouldRetryThenSucceed_WhenDeleteFailsOnceThenSucceeds() throws JsonProcessingException {
        final Message rawMessage = rawMessage(objectMapper.writeValueAsBytes(message()));
        doThrow(new OrderProjectionWriteException("Mongo down", null))
                .doNothing()
                .when(orderProjectionService).deleteById(any());

        listener.onMessage(rawMessage);

        verify(orderProjectionService, times(2)).deleteById(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRequeue_AfterExhaustingRetries() throws JsonProcessingException {
        final Message rawMessage = rawMessage(objectMapper.writeValueAsBytes(message()));
        doThrow(new OrderProjectionWriteException("Mongo down", null)).when(orderProjectionService).deleteById(any());

        assertThatThrownBy(() -> listener.onMessage(rawMessage)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(orderProjectionService, times(OrderProjectionRetryPolicy.MAX_RETRIES + 1)).deleteById(any());
    }
}
