package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderViewResponseDto(UUID orderId, UUID customerId, OrderStatus status,
        List<OrderViewItemResponseDto> items, BigDecimal totalAmount, LocalDateTime updatedAt) {

    public static OrderViewResponseDto from(final OrderView view) {
        return new OrderViewResponseDto(
                UUID.fromString(view.getId()),
                view.getCustomerId(),
                view.getStatus(),
                view.getItems().stream().map(OrderViewItemResponseDto::from).toList(),
                view.getTotalAmount(),
                view.getUpdatedAt());
    }
}
