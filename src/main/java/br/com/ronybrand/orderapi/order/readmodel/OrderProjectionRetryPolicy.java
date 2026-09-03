package br.com.ronybrand.orderapi.order.readmodel;

import java.time.Duration;

/**
 * Retry parameters for {@link OrderProjectionRabbitListener}: 3 retries after the first attempt
 * (4 attempts total), exponential backoff 1s/2s/4s, applied only to
 * {@link OrderProjectionWriteException} - never to {@link MalformedOrderProjectionMessageException}.
 * Same values as {@code notification.NotificationRetryPolicy} - not shared/reused across packages,
 * each consumer's retry policy is its own tunable, even though they happen to match today.
 */
public final class OrderProjectionRetryPolicy {

    public static final int MAX_RETRIES = 3;
    public static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    public static final double BACKOFF_MULTIPLIER = 2.0;

    private OrderProjectionRetryPolicy() {
    }
}
