package br.com.ronybrand.orderapi.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MessagingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MessagingMetrics metrics = new MessagingMetrics(registry);

    @Test
    void recordRetry_ShouldIncrementCounter_TaggedByListener() {
        metrics.recordRetry("notification");
        metrics.recordRetry("notification");
        metrics.recordRetry("projection");

        assertThat(registry.counter("messaging.retry", "listener", "notification").count()).isEqualTo(2.0);
        assertThat(registry.counter("messaging.retry", "listener", "projection").count()).isEqualTo(1.0);
    }

    @Test
    void recordDlq_ShouldIncrementCounter_TaggedByListener() {
        metrics.recordDlq("deletion");

        assertThat(registry.counter("messaging.dlq", "listener", "deletion").count()).isEqualTo(1.0);
    }

    @Test
    void recordIdempotencySkip_ShouldIncrementCounter_TaggedByListener() {
        metrics.recordIdempotencySkip("notification");

        assertThat(registry.counter("messaging.idempotency.skip", "listener", "notification").count()).isEqualTo(1.0);
    }

    @Test
    void recordOutboxPublished_ShouldIncrementCounter_TaggedByEventType() {
        metrics.recordOutboxPublished("OrderChangedEvent");
        metrics.recordOutboxPublished("OrderChangedEvent");

        assertThat(registry.counter("outbox.published", "eventType", "OrderChangedEvent").count()).isEqualTo(2.0);
    }

    @Test
    void recordOutboxPublishFailure_ShouldIncrementCounter_TaggedByEventType() {
        metrics.recordOutboxPublishFailure("OrderDeletedEvent");

        assertThat(registry.counter("outbox.publish.failure", "eventType", "OrderDeletedEvent").count()).isEqualTo(1.0);
    }

    @Test
    void recordOutboxPermanentlyFailed_ShouldIncrementCounter_TaggedByEventType() {
        metrics.recordOutboxPermanentlyFailed("OrderStatusChangedEvent");

        assertThat(registry.counter("outbox.failed.permanently", "eventType", "OrderStatusChangedEvent").count()).isEqualTo(1.0);
    }

    @Test
    void registerOutboxBacklogGauge_ShouldReflectSupplierValue() {
        final int[] backlog = {3};

        metrics.registerOutboxBacklogGauge(() -> backlog[0]);

        assertThat(registry.get("outbox.backlog").gauge().value()).isEqualTo(3.0);
        backlog[0] = 7;
        assertThat(registry.get("outbox.backlog").gauge().value()).isEqualTo(7.0);
    }
}
