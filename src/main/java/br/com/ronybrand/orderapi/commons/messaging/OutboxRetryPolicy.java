package br.com.ronybrand.orderapi.commons.messaging;

import java.time.Duration;

/**
 * Backoff schedule for {@link OutboxService#markFailed}: exponential, doubling per attempt, capped
 * at {@link #MAX_BACKOFF_SECONDS}.
 *
 * <p>Deliberately not unified with {@link RetryLoop}'s algorithm ({@code notification}'s and
 * {@code order.readmodel}'s per-listener retry policies) even though both are "exponential
 * backoff, doubling, capped" - they serve two genuinely different mechanics at two different time
 * scales. {@link RetryLoop} sleeps the listener's own consumer thread in-process, for a few
 * seconds total (see {@code NotificationRetryPolicy}/{@code OrderProjectionRetryPolicy}); this
 * schedules a future {@code available_at} for {@link OutboxPublisher}'s next poll to pick up,
 * plateauing at a full minute. Forcing one shared formula across both would either make the
 * in-process retry wait a full minute mid-message, or make the outbox reattempt sooner than a
 * crashed/overloaded downstream needs - naming and extracting each independently, the way the
 * listener policies already are, keeps both readable without pretending they're the same
 * scheduling problem.
 */
public final class OutboxRetryPolicy {

    private static final long MAX_BACKOFF_SECONDS = 60;

    private OutboxRetryPolicy() {
    }

    public static Duration nextBackoff(final int attempts) {
        return Duration.ofSeconds(Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(attempts, 6)));
    }
}
