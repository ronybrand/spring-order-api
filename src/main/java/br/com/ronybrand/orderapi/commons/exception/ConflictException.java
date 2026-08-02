package br.com.ronybrand.orderapi.commons.exception;

import java.util.Map;
import lombok.Getter;

/**
 * Uniqueness violation (e.g. duplicate taxId/passportNumber) - mapped to HTTP 409.
 */
@Getter
public class ConflictException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient Map<String, Object> params;

    public ConflictException(final String message, final ErrorCode errorCode) {
        this(message, errorCode, null);
    }

    public ConflictException(final String message, final ErrorCode errorCode, final Map<String, Object> params) {
        super(message);
        this.errorCode = errorCode;
        this.params = params;
    }
}
