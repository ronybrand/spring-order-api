package br.com.ronybrand.orderapi.customer;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerDto(
        UUID id,
        String name,
        String taxId,
        String passportNumber,
        String email,
        boolean marketingOptIn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CustomerDto from(final Customer customer) {
        return new CustomerDto(
                customer.getId(),
                customer.getName(),
                customer.getTaxId(),
                customer.getPassportNumber(),
                customer.getEmail(),
                customer.isMarketingOptIn(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
