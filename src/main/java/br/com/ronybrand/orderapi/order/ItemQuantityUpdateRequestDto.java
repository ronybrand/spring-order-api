package br.com.ronybrand.orderapi.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemQuantityUpdateRequestDto(@NotNull @Positive @Max(ItemRequestDto.MAX_QUANTITY) Integer quantity) {
}
