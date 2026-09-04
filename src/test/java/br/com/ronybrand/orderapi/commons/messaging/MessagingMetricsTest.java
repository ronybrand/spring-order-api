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
    void recordEventLost_ShouldIncrementCounter_TaggedBySource() {
        metrics.recordEventLost("order-changed");

        assertThat(registry.counter("messaging.event.lost", "source", "order-changed").count()).isEqualTo(1.0);
    }

    @Test
    void recordIdempotencySkip_ShouldIncrementCounter_TaggedByListener() {
        metrics.recordIdempotencySkip("notification");

        assertThat(registry.counter("messaging.idempotency.skip", "listener", "notification").count()).isEqualTo(1.0);
    }
}
