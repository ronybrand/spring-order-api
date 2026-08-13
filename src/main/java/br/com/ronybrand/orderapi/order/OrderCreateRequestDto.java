package br.com.ronybrand.orderapi.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code items} may be empty on creation - DOMAIN.md only requires a non-empty item list on
 * {@code confirm}, not on {@code create} (items can be added/edited afterwards while OPEN). The
 * 200-item cap (DOMAIN.md §4.6) is enforced declaratively here, not in the service.
 */
public record OrderCreateRequestDto(
        @NotNull UUID customerId,
        @NotNull @Size(max = 200) List<@Valid ItemRequestDto> items) {
}
