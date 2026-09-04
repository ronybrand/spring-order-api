package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies the notification queue's real RabbitMQ binding and dead-letter routing.
 * Listener classification and retry behavior remain covered by focused unit tests;
 * this test supplies the broker-level guarantee that a rejected payload reaches the
 * configured DLQ instead of disappearing or being requeued indefinitely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class OrderNotificationRabbitMqIT extends AbstractAuthIntegrationTest {

    private static final byte[] MALFORMED_PAYLOAD = "not-json".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void malformedMessage_ShouldReachRealDeadLetterQueue() {
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY,
                new Message(MALFORMED_PAYLOAD, new MessageProperties()));

        final Message deadLetter = rabbitTemplate.receive(RabbitMQConfig.DEAD_LETTER_QUEUE, 10_000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getBody()).isEqualTo(MALFORMED_PAYLOAD);
    }
}