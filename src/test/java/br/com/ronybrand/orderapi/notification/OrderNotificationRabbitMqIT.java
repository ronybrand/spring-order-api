package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.order.OrderStatus;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies the notification queue's real RabbitMQ binding and dead-letter routing.
 * Listener classification remains covered by focused unit tests; this test supplies
 * the broker-level guarantee that rejected payloads reach the configured DLQ and that
 * a transient SMTP failure is retried before rejection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class OrderNotificationRabbitMqIT extends AbstractAuthIntegrationTest {

    private static final byte[] MALFORMED_PAYLOAD = "not-json".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void purgeDeadLetterQueue() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.DEAD_LETTER_QUEUE, true);
    }

    @Test
    void malformedMessage_ShouldReachRealDeadLetterQueue() {
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY,
                new Message(MALFORMED_PAYLOAD, new MessageProperties()));

        final Message deadLetter = rabbitTemplate.receive(RabbitMQConfig.DEAD_LETTER_QUEUE, 10_000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getBody()).isEqualTo(MALFORMED_PAYLOAD);
    }

    @Test
    void smtpFailure_ShouldRetryAndReachRealDeadLetterQueue() {
        final UUID orderId = UUID.randomUUID();
        final double retriesBefore = meterRegistry.counter("messaging.retry", "listener", "notification").count();
        final double dlqBefore = meterRegistry.counter("messaging.dlq", "listener", "notification").count();
        final OrderStatusChangedEvent event = new OrderStatusChangedEvent(orderId, "customer@example.com",
                "Ada Lovelace", OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"),
                LocalDateTime.now(ZoneOffset.UTC));

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event);

        final Message deadLetter = rabbitTemplate.receive(RabbitMQConfig.DEAD_LETTER_QUEUE, 15_000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getBody()).contains(orderId.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(meterRegistry.counter("messaging.retry", "listener", "notification").count())
                .isEqualTo(retriesBefore + 3);
        assertThat(meterRegistry.counter("messaging.dlq", "listener", "notification").count())
                .isEqualTo(dlqBefore + 1);
    }
}