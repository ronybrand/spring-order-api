package br.com.ronybrand.orderapi.customer;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body of a customer. {@code taxId}, {@code passportNumber} and {@code email} are PII and
 * deliberately absent: no consumer of this API needs them back (the caller that created the record
 * already has them), and the DTO is also what {@code CustomerService.findById} caches in Redis.
 */
public record CustomerDto(
        UUID id,
        String name,
        boolean marketingOptIn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CustomerDto from(final Customer customer) {
        return new CustomerDto(
                customer.getId(),
                customer.getName(),
                customer.isMarketingOptIn(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
