package br.com.ronybrand.orderapi.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ItemRequestDto(
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
        @NotNull @Positive @Max(ItemRequestDto.MAX_QUANTITY) Integer quantity) {

    /** Per line item: keeps a single order total far from absurd values. */
    public static final int MAX_QUANTITY = 10_000;
}
