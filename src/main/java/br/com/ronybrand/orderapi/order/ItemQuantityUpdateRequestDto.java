package br.com.ronybrand.orderapi.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemQuantityUpdateRequestDto(@NotNull @Positive Integer quantity) {
}
