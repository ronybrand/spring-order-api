package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.ronybrand.orderapi.order.OrderStatus;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real integration against Redis (Testcontainers), not just a mocked {@code StringRedisTemplate}
 * like {@link OrderNotificationRabbitListenerTest} - follows the project convention that a new
 * external-service integration gets an end-to-end test against the real thing, not only a client
 * mock. Deliberately not a {@code @SpringBootTest}: unlike MongoDB, adding
 * {@code spring-boot-starter-data-redis} does not force a connection at context startup (Lettuce
 * connects lazily), so this test wires a minimal {@link StringRedisTemplate} by hand against its
 * own isolated container instead of paying for the whole application context.
 */
class OrderNotificationIdempotencyIT {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate stringRedisTemplate;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    private static OrderStatusChangedEvent event() {
        return new OrderStatusChangedEvent(UUID.randomUUID(), "ada@example.com", "Ada Lovelace",
                OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"), LocalDateTime.now(ZoneOffset.UTC));
    }

    private static OrderNotificationRabbitListener listener(final EmailService emailService, final ObjectMapper objectMapper) {
        return new OrderNotificationRabbitListener(emailService, objectMapper, stringRedisTemplate);
    }

    @Test
    void onMessage_ShouldSendOnlyOnce_WhenTheSameMessageIsProcessedTwice() throws JsonProcessingException {
        final EmailService emailService = mock(EmailService.class);
        final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        final OrderNotificationRabbitListener listener = listener(emailService, objectMapper);
        final Message message = new Message(objectMapper.writeValueAsBytes(event()), new MessageProperties());

        listener.onMessage(message);
        listener.onMessage(message);

        verify(emailService, times(1)).sendOrderStatusEmail(any());
    }

    @Test
    void idempotencyKey_ShouldExistInRedis_AfterSuccessfulSend() throws JsonProcessingException {
        final EmailService emailService = mock(EmailService.class);
        final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        final OrderNotificationRabbitListener listener = listener(emailService, objectMapper);
        final OrderStatusChangedEvent event = event();
        final Message message = new Message(objectMapper.writeValueAsBytes(event), new MessageProperties());

        listener.onMessage(message);

        final String key = "notification:sent:" + event.orderId() + ":" + event.newStatus();
        assertThat(stringRedisTemplate.hasKey(key)).isTrue();
    }
}
