package br.com.ronybrand.orderapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.commons.messaging.OutboxEvent;
import br.com.ronybrand.orderapi.commons.messaging.OutboxEventRepository;
import br.com.ronybrand.orderapi.commons.messaging.OutboxPublisher;
import br.com.ronybrand.orderapi.commons.messaging.OutboxService;
import br.com.ronybrand.orderapi.order.OrderStatus;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the outbox's actual delivery contract end-to-end: at-least-once delivery plus a
 * downstream idempotency guard, not exactly-once delivery (see ADR 0006 and
 * {@link OrderNotificationRabbitListener}'s Redis claim).
 *
 * <p>{@link OutboxPublisher#publishPending()} only calls {@code markPublished} *after* the broker
 * send succeeds - so a crash in that gap (the send lands, the instance dies before the
 * {@code PUBLISHED} row update commits) leaves the row claimable again, and the next poll
 * republishes the identical event. That gap is reproduced here directly through the outbox's
 * public API - no mocking of Rabbit/Postgres internals - by publishing an event, then using
 * {@link OutboxEvent#markRetry} (the same public transition {@code markFailed} uses) to put the
 * row back in {@code PENDING} as if the {@code PUBLISHED} update never happened, and publishing it
 * a second time. That drives two genuinely independent AMQP deliveries of the same payload through
 * the real listener, so a passing test demonstrates the whole pipeline (outbox to broker to
 * consumer) collapses a redelivery into a single side effect, not just the listener in isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.rabbitmq.listener.simple.auto-startup=true", "app.outbox.poll-delay-ms=3600000"})
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class OutboxRedeliveryIdempotencyIT extends AbstractAuthIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private EmailService emailService;

    @Test
    void redeliveredOutboxEvent_ShouldTriggerTheEffectOnlyOnce_WhenPublishedTwiceBeforeMarkPublished() {
        final UUID orderId = UUID.randomUUID();
        final OrderStatusChangedEvent event = new OrderStatusChangedEvent(orderId, "ada@example.com",
                "Ada Lovelace", OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"),
                LocalDateTime.now(ZoneOffset.UTC));
        outboxService.enqueue("OrderStatusChangedEvent", orderId, RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY,
                event);

        outboxPublisher.publishPending();
        final OutboxEvent published = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(orderId))
                .findFirst().orElseThrow();
        // Simulate a crash between the broker send and the PUBLISHED row commit: put the row back
        // in PENDING, exactly as markFailed would after a real transient failure, so the next poll
        // reclaims and republishes the very same payload.
        published.markRetry(LocalDateTime.now(ZoneOffset.UTC), "simulated crash before markPublished");
        outboxEventRepository.save(published);

        outboxPublisher.publishPending();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> verify(emailService, times(1)).sendOrderStatusEmail(any()));
        final OutboxEvent finalState = outboxEventRepository.findById(published.getId()).orElseThrow();
        assertThat(finalState.getStatus().name()).isEqualTo("PUBLISHED");
    }
}
