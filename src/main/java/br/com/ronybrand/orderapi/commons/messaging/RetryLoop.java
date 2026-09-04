package br.com.ronybrand.orderapi.commons.messaging;

import java.time.Duration;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Shared attempt-counter/exponential-backoff/interrupt-handling mechanics for the manual retry
 * loop every {@code MessageListener} in this codebase runs (parse-or-throw straight to DLQ, then
 * retry a specific transient-failure exception type with backoff up to a policy's max, then reject
 * without requeue). Each listener still owns its own retry policy values and log wording - only the
 * mechanical part (easy to subtly diverge when copy-pasted, e.g. an off-by-one on the attempt
 * count or a missed {@code Thread.currentThread().interrupt()}) is centralized here.
 */
public final class RetryLoop {

    private RetryLoop() {
    }

    @FunctionalInterface
    public interface Attempt {
        void run();
    }

    @FunctionalInterface
    public interface RetryListener<E extends RuntimeException> {
        void onTransientFailure(int attempt, int maxAttempts, long backoffMs, E exception);
    }

    @FunctionalInterface
    public interface ExhaustionListener<E extends RuntimeException> {
        void onExhausted(int attempts, E exception);
    }

    /**
     * Runs {@code attempt} until it succeeds, a non-{@code retryableType} exception escapes (which
     * propagates immediately, uncaught), or {@code maxRetries} is exceeded - at which point it
     * throws {@link AmqpRejectAndDontRequeueException} so the AMQP container's dead-letter wiring
     * routes the message to its DLQ instead of requeueing it forever.
     */
    public static <E extends RuntimeException> void run(final Class<E> retryableType, final int maxRetries,
            final Duration initialBackoff, final double backoffMultiplier, final Attempt attempt,
            final RetryListener<E> onRetry, final ExhaustionListener<E> onExhausted) {
        long backoffMs = initialBackoff.toMillis();
        for (int attemptNumber = 1; ; attemptNumber++) {
            try {
                attempt.run();
                return;
            } catch (final RuntimeException e) {
                if (!retryableType.isInstance(e)) {
                    throw e;
                }
                final E typed = retryableType.cast(e);
                if (attemptNumber > maxRetries) {
                    onExhausted.onExhausted(attemptNumber, typed);
                    throw new AmqpRejectAndDontRequeueException("Exhausted retries", typed);
                }
                onRetry.onTransientFailure(attemptNumber, maxRetries + 1, backoffMs, typed);
                sleep(backoffMs);
                backoffMs = (long) (backoffMs * backoffMultiplier);
            }
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }
}
