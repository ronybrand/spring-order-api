package br.com.ronybrand.orderapi.commons.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Turns the retry/DLQ/idempotency-skip behavior already logged by every manual
 * {@code MessageListener} in this codebase, plus the outbox publish lifecycle
 * ({@link br.com.ronybrand.orderapi.commons.messaging.OutboxPublisher}), into counters too - a log
 * line is not queryable/alertable the way a metric is, and this is exactly the kind of system
 * (async messaging with retry, DLQ and a durable outbox) where that gap matters most. Centralized
 * here so the metric names/tag keys can't drift between call sites the way independently-added
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

    /** A message was skipped because it was already processed (Redis idempotency claim). */
    public void recordIdempotencySkip(final String listener) {
        meterRegistry.counter("messaging.idempotency.skip", "listener", listener).increment();
    }

    /** An outbox event was published to RabbitMQ and acknowledged by the broker. */
    public void recordOutboxPublished(final String eventType) {
        meterRegistry.counter("outbox.published", "eventType", eventType).increment();
    }

    /** An outbox publish attempt failed (transient - the event stays PENDING for retry, unless this was its last attempt). */
    public void recordOutboxPublishFailure(final String eventType) {
        meterRegistry.counter("outbox.publish.failure", "eventType", eventType).increment();
    }

    /** An outbox event exhausted its retry budget and moved to {@code FAILED} - needs manual attention. */
    public void recordOutboxPermanentlyFailed(final String eventType) {
        meterRegistry.counter("outbox.failed.permanently", "eventType", eventType).increment();
    }

    /**
     * Registers a gauge sampling {@code backlogSupplier} on each scrape - the count of outbox
     * events still {@code PENDING}/{@code PROCESSING}, i.e. work the publisher hasn't cleared yet.
     * A growing backlog (publisher down, broker unreachable for longer than the retry budget can
     * absorb) is exactly the kind of condition a counter alone wouldn't surface.
     */
    public void registerOutboxBacklogGauge(final Supplier<Number> backlogSupplier) {
        Gauge.builder("outbox.backlog", backlogSupplier).register(meterRegistry);
    }
}
