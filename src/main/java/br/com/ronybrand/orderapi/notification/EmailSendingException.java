package br.com.ronybrand.orderapi.notification;

/**
 * Transient failure sending the notification e-mail (e.g. SMTP unavailable). Classification-only -
 * {@link OrderNotificationRabbitListener} retries this with backoff before giving up to the DLQ
 * (DOMAIN.md §5, rule 2).
 */
public class EmailSendingException extends RuntimeException {

    public EmailSendingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
