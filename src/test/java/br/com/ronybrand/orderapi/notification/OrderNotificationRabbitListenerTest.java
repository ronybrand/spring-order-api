package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.ronybrand.orderapi.order.OrderStatus;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class OrderNotificationRabbitListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OrderNotificationRabbitListener listener = new OrderNotificationRabbitListener(emailService, objectMapper);

    private static Message rawMessage(final byte[] body) {
        return new Message(body, new MessageProperties());
    }

    private static OrderStatusChangedEvent event() {
        return new OrderStatusChangedEvent(UUID.randomUUID(), "ada@example.com", "Ada Lovelace",
                OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"), LocalDateTime.now());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenPayloadIsNotValidJson() throws Exception {
        final Message message = rawMessage("not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(emailService, never()).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenRequiredFieldIsMissing() throws Exception {
        final String jsonMissingField = "{\"orderId\":\"" + UUID.randomUUID() + "\"}";
        final Message message = rawMessage(jsonMissingField.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(emailService, never()).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldSucceed_WhenPayloadIsValidAndEmailSucceeds() throws Exception {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));

        listener.onMessage(message);

        verify(emailService, times(1)).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldRetryThenSucceed_WhenEmailFailsOnceThenSucceeds() throws Exception {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));
        doThrow(new EmailSendingException("SMTP down", null))
                .doNothing()
                .when(emailService).sendOrderStatusEmail(any());

        listener.onMessage(message);

        verify(emailService, times(2)).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRequeue_AfterExhaustingRetries() throws Exception {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));
        doThrow(new EmailSendingException("SMTP down", null)).when(emailService).sendOrderStatusEmail(any());

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(emailService, times(NotificationRetryPolicy.MAX_RETRIES + 1)).sendOrderStatusEmail(any());
    }
}
