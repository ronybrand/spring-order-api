package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.ronybrand.orderapi.order.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class OrderProjectionRabbitListenerTest {

    private final OrderProjectionService orderProjectionService = mock(OrderProjectionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OrderProjectionRabbitListener listener = new OrderProjectionRabbitListener(orderProjectionService, objectMapper);

    private static Message rawMessage(final byte[] body) {
        return new Message(body, new MessageProperties());
    }

    private static OrderProjectionMessage message() {
        final OrderProjectionItem item = new OrderProjectionItem(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 1, new BigDecimal("10.00"));
        return new OrderProjectionMessage(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.OPEN, List.of(item),
                new BigDecimal("10.00"), LocalDateTime.now());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenPayloadIsNotValidJson() {
        final Message message = rawMessage("not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(orderProjectionService, never()).upsert(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenRequiredFieldIsMissing() {
        final String jsonMissingField = "{\"orderId\":\"" + UUID.randomUUID() + "\"}";
        final Message message = rawMessage(jsonMissingField.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(orderProjectionService, never()).upsert(any());
    }

    @Test
    void onMessage_ShouldAccept_WhenItemsListIsEmpty() throws JsonProcessingException {
        final OrderProjectionMessage message = new OrderProjectionMessage(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.OPEN,
                List.of(), BigDecimal.ZERO, LocalDateTime.now());
        final Message rawMessage = rawMessage(objectMapper.writeValueAsBytes(message));

        listener.onMessage(rawMessage);

        verify(orderProjectionService, times(1)).upsert(any());
    }

    @Test
    void onMessage_ShouldSucceed_WhenPayloadIsValidAndUpsertSucceeds() throws JsonProcessingException {
        final Message rawMessage = rawMessage(objectMapper.writeValueAsBytes(message()));

        listener.onMessage(rawMessage);

        verify(orderProjectionService, times(1)).upsert(any());
    }

    @Test
    void onMessage_ShouldRetryThenSucceed_WhenUpsertFailsOnceThenSucceeds() throws JsonProcessingException {
        final Message rawMessage = rawMessage(objectMapper.writeValueAsBytes(message()));
        doThrow(new OrderProjectionWriteException("Mongo down", null))
                .doNothing()
                .when(orderProjectionService).upsert(any());

        listener.onMessage(rawMessage);

        verify(orderProjectionService, times(2)).upsert(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRequeue_AfterExhaustingRetries() throws JsonProcessingException {
        final Message rawMessage = rawMessage(objectMapper.writeValueAsBytes(message()));
        doThrow(new OrderProjectionWriteException("Mongo down", null)).when(orderProjectionService).upsert(any());

        assertThatThrownBy(() -> listener.onMessage(rawMessage)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(orderProjectionService, times(OrderProjectionRetryPolicy.MAX_RETRIES + 1)).upsert(any());
    }
}
