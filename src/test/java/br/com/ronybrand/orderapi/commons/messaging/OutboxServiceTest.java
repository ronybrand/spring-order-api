package br.com.ronybrand.orderapi.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class OutboxServiceTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessagingMetrics messagingMetrics = mock(MessagingMetrics.class);
    private final OutboxService outboxService = new OutboxService(repository, objectMapper, messagingMetrics);

    private record Payload(String value) {
    }

    @BeforeEach
    void registerGauge() {
        outboxService.registerBacklogGauge();

        verify(messagingMetrics).registerOutboxBacklogGauge(any());
    }

    @Test
    void enqueue_ShouldSaveAPendingEvent_WithSerializedPayload() {
        final UUID aggregateId = UUID.randomUUID();

        outboxService.enqueue("OrderChangedEvent", aggregateId, "orders.exchange", "orders.changed",
                new Payload("hello"));

        final ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        final OutboxEvent saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventType()).isEqualTo("OrderChangedEvent");
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getExchangeName()).isEqualTo("orders.exchange");
        assertThat(saved.getRoutingKey()).isEqualTo("orders.changed");
        assertThat(saved.getPayload()).isEqualTo("{\"value\":\"hello\"}");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getAvailableAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void enqueue_ShouldWrapSerializationFailure_InIllegalStateException() {
        final Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this;
            }
        };

        assertThatThrownBy(() -> outboxService.enqueue("Bad", UUID.randomUUID(), "ex", "rk", unserializable))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void claimBatch_ShouldMarkClaimedEventsAsProcessing_AndReturnThem() {
        final OutboxEvent event = OutboxEvent.builder().id(UUID.randomUUID()).status(OutboxStatus.PENDING)
                .attempts(0).build();
        when(repository.findClaimable(any(), any(), any(Pageable.class))).thenReturn(List.of(event));

        final List<OutboxEvent> claimed = outboxService.claimBatch();

        assertThat(claimed).containsExactly(event);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getLockedAt()).isNotNull();
    }

    @Test
    void markPublished_ShouldMarkEventPublished_AndPersistIt() {
        final OutboxEvent event = OutboxEvent.builder().id(UUID.randomUUID()).status(OutboxStatus.PROCESSING)
                .attempts(0).build();

        outboxService.markPublished(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void markFailed_ShouldRescheduleForRetry_BelowAttemptThreshold() {
        final OutboxEvent event = OutboxEvent.builder().id(UUID.randomUUID()).status(OutboxStatus.PROCESSING)
                .attempts(0).availableAt(LocalDateTime.now()).build();

        outboxService.markFailed(event, new RuntimeException("boom"));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("boom");
        verify(repository, times(1)).save(event);
    }

    @Test
    void markFailed_ShouldMoveToFailed_AfterExhaustingAttempts() {
        final OutboxEvent event = OutboxEvent.builder().id(UUID.randomUUID()).status(OutboxStatus.PROCESSING)
                .attempts(4).availableAt(LocalDateTime.now()).build();

        outboxService.markFailed(event, new RuntimeException("still failing"));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(5);
    }

    @Test
    void registerBacklogGauge_ShouldDelegateCountToRepository_ForPendingAndProcessingStatuses() {
        when(repository.countByStatusIn(List.of(OutboxStatus.PENDING, OutboxStatus.PROCESSING))).thenReturn(3L);

        final ArgumentCaptor<java.util.function.Supplier<Number>> captor = ArgumentCaptor.forClass(java.util.function.Supplier.class);
        verify(messagingMetrics).registerOutboxBacklogGauge(captor.capture());

        assertThat(captor.getValue().get()).isEqualTo(3L);
    }
}
