package br.com.ronybrand.orderapi.order.readmodel;

/**
 * Failure persisting an {@link OrderView} upsert to MongoDB. Classification-only -
 * {@code OrderProjectionRabbitListener} retries this with backoff before giving up to the DLQ,
 * same contract as {@code EmailSendingException} on the notification path.
 */
public class OrderProjectionWriteException extends RuntimeException {

    public OrderProjectionWriteException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
