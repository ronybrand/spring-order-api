package br.com.ronybrand.orderapi.notification;

/**
 * Marker for a message that is not valid JSON, or is valid JSON missing a required
 * {@link br.com.ronybrand.orderapi.order.OrderStatusChangedEvent} field. Classification-only -
 * {@link OrderNotificationRabbitListener} never retries this, straight to the DLQ on the first
 * attempt (DOMAIN.md §5, rule 1).
 */
public class MalformedOrderStatusMessageException extends RuntimeException {

    public MalformedOrderStatusMessageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
