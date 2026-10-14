package br.com.ronybrand.orderapi.commons.config;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

/**
 * Writes the standard {@code {message, code, params, requestId}} error body from a servlet filter, where
 * {@code @RestControllerAdvice} does not reach. The request id was put in the MDC by {@link RequestIdFilter},
 * which runs before every filter that rejects a request.
 */
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static void write(final HttpServletResponse response, final HttpStatus status, final ErrorCode errorCode)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(body(errorCode));
    }

    static String body(final ErrorCode errorCode) {
        final String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return "{\"message\":\"" + errorCode.getDescription() + "\",\"code\":\"" + errorCode.getCode()
                + "\",\"params\":{},\"requestId\":" + (requestId == null ? "null" : "\"" + requestId + "\"") + "}";
    }
}
