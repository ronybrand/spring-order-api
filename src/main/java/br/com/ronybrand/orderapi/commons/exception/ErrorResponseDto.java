package br.com.ronybrand.orderapi.commons.exception;

import java.util.Map;

/**
 * Standard error response body. {@code requestId} correlates the client-side response with the
 * server logs (same id propagated via the {@code X-Request-Id} header).
 */
public record ErrorResponseDto(String message, String code, Map<String, Object> params, String requestId) {
}
