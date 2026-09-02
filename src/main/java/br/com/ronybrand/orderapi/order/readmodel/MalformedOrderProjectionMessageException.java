package br.com.ronybrand.orderapi.order.readmodel;

/**
 * Marker for a message that is not valid JSON, or is valid JSON missing a required
 * {@link OrderProjectionMessage} field. Classification-only -
 * {@link OrderProjectionRabbitListener} never retries this, straight to the DLQ on the first
 * attempt - same contract as {@code notification.MalformedOrderStatusMessageException}.
 */
public class MalformedOrderProjectionMessageException extends RuntimeException {

    public MalformedOrderProjectionMessageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
