package br.com.ronybrand.orderapi.order.readmodel;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderViewItemResponseDto(UUID id, String description, BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {

    public static OrderViewItemResponseDto from(final OrderViewItem item) {
        return new OrderViewItemResponseDto(item.getId(), item.getDescription(), item.getUnitPrice(), item.getQuantity(),
                item.getSubtotal());
    }
}
