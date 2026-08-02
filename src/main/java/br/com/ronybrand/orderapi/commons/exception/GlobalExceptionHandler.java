package br.com.ronybrand.orderapi.commons.exception;

import br.com.ronybrand.orderapi.commons.config.RequestIdFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Single exception -> HTTP response translation point. Grows incrementally: a new
 * {@code @ExceptionHandler} only lands when the corresponding use case can actually throw that
 * exception (e.g. {@link ResourceNotFoundException} arrives together with the first
 * {@code findById}).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(final MethodArgumentNotValidException ex) {
        final Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (final FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_MISSING_FIELD, fieldErrors);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFound(final ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), ex.getParams());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(final MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_CONSTRAINT_VIOLATION,
                "Invalid value for parameter '" + ex.getName() + "'", null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDto> handleConflict(final ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), ex.getParams());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(final DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT_DATA_INTEGRITY_VIOLATION, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(final AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.AUTHORIZATION_ACCESS_DENIED, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(final Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ErrorResponseDto> build(final HttpStatus status, final ErrorCode errorCode,
            final Map<String, Object> params) {
        return build(status, errorCode, errorCode.getDescription(), params);
    }

    private ResponseEntity<ErrorResponseDto> build(final HttpStatus status, final ErrorCode errorCode,
            final String message, final Map<String, Object> params) {
        final ErrorResponseDto body = new ErrorResponseDto(message, errorCode.getCode(), params, currentRequestId());
        return ResponseEntity.status(status).body(body);
    }

    private String currentRequestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
