package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ronybrand.orderapi.order.OrderStatus;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OrderNotificationRabbitListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final OrderNotificationRabbitListener listener =
            new OrderNotificationRabbitListener(emailService, objectMapper, stringRedisTemplate);

    {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    private static Message rawMessage(final byte[] body) {
        return new Message(body, new MessageProperties());
    }

    private static OrderStatusChangedEvent event() {
        return new OrderStatusChangedEvent(UUID.randomUUID(), "ada@example.com", "Ada Lovelace",
                OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"), LocalDateTime.now());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenPayloadIsNotValidJson() {
        final Message message = rawMessage("not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(emailService, never()).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRetry_WhenRequiredFieldIsMissing() {
        final String jsonMissingField = "{\"orderId\":\"" + UUID.randomUUID() + "\"}";
        final Message message = rawMessage(jsonMissingField.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(emailService, never()).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldSucceed_WhenPayloadIsValidAndEmailSucceeds() throws JsonProcessingException {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));

        listener.onMessage(message);

        verify(emailService, times(1)).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldRetryThenSucceed_WhenEmailFailsOnceThenSucceeds() throws JsonProcessingException {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));
        doThrow(new EmailSendingException("SMTP down", null))
                .doNothing()
                .when(emailService).sendOrderStatusEmail(any());

        listener.onMessage(message);

        verify(emailService, times(2)).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldRejectWithoutRequeue_AfterExhaustingRetries() throws JsonProcessingException {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));
        doThrow(new EmailSendingException("SMTP down", null)).when(emailService).sendOrderStatusEmail(any());

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(emailService, times(NotificationRetryPolicy.MAX_RETRIES + 1)).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldSkipSending_WhenClaimNotAcquired() throws JsonProcessingException {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        listener.onMessage(message);

        verify(emailService, never()).sendOrderStatusEmail(any());
    }

    @Test
    void onMessage_ShouldClaimAtomicallyBeforeSending_WithCorrectKeyAndTtl() throws JsonProcessingException {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));
        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        listener.onMessage(message);

        verify(emailService, times(1)).sendOrderStatusEmail(any());
        verify(valueOperations).setIfAbsent(keyCaptor.capture(), eq("1"), eq(Duration.ofHours(24)));
        assertThat(keyCaptor.getValue())
                .contains(event.orderId().toString())
                .contains(event.newStatus().name());
    }

    @Test
    void onMessage_ShouldReleaseClaim_WhenSendExhaustsRetries() throws JsonProcessingException {
        final OrderStatusChangedEvent event = event();
        final Message message = rawMessage(objectMapper.writeValueAsBytes(event));
        doThrow(new EmailSendingException("SMTP down", null)).when(emailService).sendOrderStatusEmail(any());
        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(stringRedisTemplate).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue())
                .contains(event.orderId().toString())
                .contains(event.newStatus().name());
    }
}
