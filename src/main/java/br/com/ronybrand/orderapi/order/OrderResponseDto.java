package br.com.ronybrand.orderapi.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(UUID id, UUID customerId, List<ItemResponseDto> items, BigDecimal total,
        OrderStatus status, Long version, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static OrderResponseDto from(final Order order) {
        return new OrderResponseDto(
                order.getId(),
                order.getCustomer().getId(),
                order.getItems().stream().map(ItemResponseDto::from).toList(),
                order.getTotal(),
                order.getStatus(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
