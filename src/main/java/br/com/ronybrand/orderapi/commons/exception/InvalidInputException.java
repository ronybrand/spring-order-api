package br.com.ronybrand.orderapi.commons.exception;

import java.util.Map;
import lombok.Getter;

/**
 * Business rule violation (e.g. invalid status transition, order not editable) - mapped to
 * HTTP 400.
 */
@Getter
public class InvalidInputException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient Map<String, Object> params;

    public InvalidInputException(final String message, final ErrorCode errorCode) {
        this(message, errorCode, null);
    }

    public InvalidInputException(final String message, final ErrorCode errorCode, final Map<String, Object> params) {
        super(message);
        this.errorCode = errorCode;
        this.params = params;
    }
}
