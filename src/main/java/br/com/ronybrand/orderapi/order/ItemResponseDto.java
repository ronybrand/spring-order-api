package br.com.ronybrand.orderapi.order;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code subtotal} is computed here, never persisted (DOMAIN.md §2).
 */
public record ItemResponseDto(UUID id, String description, BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {

    public static ItemResponseDto from(final Item item) {
        return new ItemResponseDto(item.getId(), item.getDescription(), item.getUnitPrice(), item.getQuantity(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
