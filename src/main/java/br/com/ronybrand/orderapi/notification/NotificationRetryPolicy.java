package br.com.ronybrand.orderapi.notification;

import java.time.Duration;

/**
 * Retry parameters for {@link OrderNotificationRabbitListener}: 3 retries after the first attempt
 * (4 attempts total), exponential backoff 1s/2s/4s, applied only to {@link EmailSendingException}
 * - never to {@link MalformedOrderStatusMessageException} (DOMAIN.md §5).
 */
public final class NotificationRetryPolicy {

    public static final int MAX_RETRIES = 3;
    public static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    public static final double BACKOFF_MULTIPLIER = 2.0;

    private NotificationRetryPolicy() {
    }
}
