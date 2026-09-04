package br.com.ronybrand.orderapi.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxPublisherTest {

    private final OutboxService outboxService = mock(OutboxService.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final MessagingMetrics messagingMetrics = mock(MessagingMetrics.class);
    private final OutboxPublisher publisher = new OutboxPublisher(outboxService, rabbitTemplate, messagingMetrics);

    private static OutboxEvent event(final OutboxStatus status, final int attempts) {
        return OutboxEvent.builder().id(UUID.randomUUID()).eventType("OrderChangedEvent")
                .aggregateId(UUID.randomUUID()).exchangeName("orders.exchange").routingKey("orders.changed")
                .payload("{}").status(status).attempts(attempts).build();
    }

    @Test
    void publishPending_ShouldSendToRabbitAndMarkPublished_WhenSendSucceeds() {
        final OutboxEvent claimed = event(OutboxStatus.PROCESSING, 0);
        when(outboxService.claimBatch()).thenReturn(List.of(claimed));

        publisher.publishPending();

        verify(rabbitTemplate).send(eq(claimed.getExchangeName()), eq(claimed.getRoutingKey()), any(Message.class));
        verify(outboxService).markPublished(claimed);
        verify(outboxService, never()).markFailed(any(), any());
        verify(messagingMetrics).recordOutboxPublished(claimed.getEventType());
        verify(messagingMetrics, never()).recordOutboxPublishFailure(anyString());
    }

    @Test
    void publishPending_ShouldMarkFailed_WhenRabbitSendThrows() {
        final OutboxEvent claimed = event(OutboxStatus.PROCESSING, 0);
        when(outboxService.claimBatch()).thenReturn(List.of(claimed));
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpException("broker unreachable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        publisher.publishPending();

        verify(outboxService, never()).markPublished(any());
        verify(outboxService).markFailed(eq(claimed), any(RuntimeException.class));
        verify(messagingMetrics).recordOutboxPublishFailure(claimed.getEventType());
        verify(messagingMetrics, never()).recordOutboxPermanentlyFailed(anyString());
    }

    @Test
    void publishPending_ShouldRecordPermanentFailure_WhenEventEndsUpFailedAfterMarkFailed() {
        final OutboxEvent claimed = event(OutboxStatus.PROCESSING, 4);
        when(outboxService.claimBatch()).thenReturn(List.of(claimed));
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpException("broker unreachable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            claimed.markRetry(claimed.getAvailableAt(), "boom");
            return null;
        }).when(outboxService).markFailed(eq(claimed), any());

        publisher.publishPending();

        assertThat(claimed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(messagingMetrics).recordOutboxPermanentlyFailed(claimed.getEventType());
    }

    @Test
    void publishPending_ShouldProcessEachClaimedEventIndependently_WhenOneFailsAndOthersSucceed() {
        final OutboxEvent failing = event(OutboxStatus.PROCESSING, 0);
        final OutboxEvent succeeding = event(OutboxStatus.PROCESSING, 0);
        when(outboxService.claimBatch()).thenReturn(List.of(failing, succeeding));
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpException("broker unreachable"))
                .when(rabbitTemplate).send(eq(failing.getExchangeName()), eq(failing.getRoutingKey()), any(Message.class));

        publisher.publishPending();

        verify(outboxService).markFailed(eq(failing), any());
        verify(outboxService).markPublished(succeeding);
        verify(rabbitTemplate, times(2)).send(anyString(), anyString(), any(Message.class));
    }
}
