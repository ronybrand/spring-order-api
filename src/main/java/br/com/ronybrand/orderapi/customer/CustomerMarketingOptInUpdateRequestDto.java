package br.com.ronybrand.orderapi.customer;

import jakarta.validation.constraints.NotNull;

public record CustomerMarketingOptInUpdateRequestDto(@NotNull Boolean marketingOptIn) {
}
