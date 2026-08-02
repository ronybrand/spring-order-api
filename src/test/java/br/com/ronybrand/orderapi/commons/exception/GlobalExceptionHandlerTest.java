package br.com.ronybrand.orderapi.commons.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_ShouldReturn400WithFieldErrors() {
        final BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("customer", "taxId", "must not be blank")));
        final MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        final ResponseEntity<ErrorResponseDto> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_MISSING_FIELD.getCode());
        assertThat(response.getBody().params()).containsEntry("taxId", "must not be blank");
    }

    @Test
    void handleConflict_ShouldReturn409WithExceptionMessageAndErrorCode() {
        final ConflictException ex = new ConflictException("Tax ID already exists", ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS);

        final ResponseEntity<ErrorResponseDto> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS.getCode());
        assertThat(response.getBody().message()).isEqualTo("Tax ID already exists");
    }

    @Test
    void handleResourceNotFound_ShouldReturn404WithExceptionMessageAndErrorCode() {
        final ResourceNotFoundException ex = new ResourceNotFoundException("Customer not found", ErrorCode.RESOURCE_NOT_FOUND_CUSTOMER);

        final ResponseEntity<ErrorResponseDto> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND_CUSTOMER.getCode());
        assertThat(response.getBody().message()).isEqualTo("Customer not found");
    }

    @Test
    void handleTypeMismatch_ShouldReturn400() {
        final MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");

        final ResponseEntity<ErrorResponseDto> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_CONSTRAINT_VIOLATION.getCode());
        assertThat(response.getBody().message()).contains("id");
    }

    @Test
    void handleDataIntegrityViolation_ShouldReturn409() {
        final ResponseEntity<ErrorResponseDto> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("constraint"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.CONFLICT_DATA_INTEGRITY_VIOLATION.getCode());
    }

    @Test
    void handleAccessDenied_ShouldReturn403() {
        final ResponseEntity<ErrorResponseDto> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.AUTHORIZATION_ACCESS_DENIED.getCode());
    }

    @Test
    void handleUnexpected_ShouldReturn500_AndNeverLeakExceptionMessageToClient() {
        final ResponseEntity<ErrorResponseDto> response = handler.handleUnexpected(new RuntimeException("secret internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.getBody().message()).doesNotContain("secret internal detail");
    }
}
