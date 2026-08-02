package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.validation.Patterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create and update payload for Customer (same DTO for both - see the DTO conventions in the
 * {@code spring-feature} skill).
 */
public record CustomerRequestDto(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = Patterns.TAX_ID) String taxId,
        @Pattern(regexp = Patterns.PASSPORT_NUMBER) String passportNumber,
        @NotBlank @Email @Pattern(regexp = Patterns.EMAIL) String email) {
}
