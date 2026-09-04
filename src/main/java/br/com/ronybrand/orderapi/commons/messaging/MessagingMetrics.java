package br.com.ronybrand.orderapi.commons.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Turns the retry/DLQ/lost-event/idempotency-skip behavior already logged by every manual
 * {@code MessageListener} (and the event-to-RabbitMQ bridges) in this codebase into counters too -
 * a log line is not queryable/alertable the way a metric is, and this is exactly the kind of
 * system (async messaging with retry and DLQ) where that gap matters most. Centralized here so the
 * metric names/tag keys can't drift between call sites the way independently-added
 * {@code meterRegistry.counter(...)} calls would.
 */
@Component
public class MessagingMetrics {

    private final MeterRegistry meterRegistry;

    public MessagingMetrics(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** A transient failure is about to be retried with backoff. */
    public void recordRetry(final String listener) {
        meterRegistry.counter("messaging.retry", "listener", listener).increment();
    }

    /** Retries exhausted (or payload malformed) - message rejected to its DLQ. */
    public void recordDlq(final String listener) {
        meterRegistry.counter("messaging.dlq", "listener", listener).increment();
    }

    /** An in-process event failed to publish to RabbitMQ and is permanently lost (never retried). */
    public void recordEventLost(final String source) {
        meterRegistry.counter("messaging.event.lost", "source", source).increment();
    }

    /** A message was skipped because it was already processed (Redis idempotency claim). */
    public void recordIdempotencySkip(final String listener) {
        meterRegistry.counter("messaging.idempotency.skip", "listener", listener).increment();
    }
}
