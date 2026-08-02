package br.com.ronybrand.orderapi.commons.exception;

import java.util.Map;
import lombok.Getter;

/**
 * Lookup by id that found nothing - mapped to HTTP 404.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient Map<String, Object> params;

    public ResourceNotFoundException(final String message, final ErrorCode errorCode) {
        this(message, errorCode, null);
    }

    public ResourceNotFoundException(final String message, final ErrorCode errorCode, final Map<String, Object> params) {
        super(message);
        this.errorCode = errorCode;
        this.params = params;
    }
}
