package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProjectionService {

    private final OrderViewRepository orderViewRepository;

    public void upsert(final OrderProjectionMessage message) {
        try {
            orderViewRepository.save(toOrderView(message));
        } catch (final DataAccessException e) {
            throw new OrderProjectionWriteException("Failed to upsert order view: orderId=" + message.orderId(), e);
        }
        log.info("Order view upserted: orderId={}", message.orderId());
    }

    public void deleteById(final UUID orderId) {
        try {
            orderViewRepository.deleteById(orderId.toString());
        } catch (final DataAccessException e) {
            throw new OrderProjectionWriteException("Failed to delete order view: orderId=" + orderId, e);
        }
        log.info("Order view deleted: orderId={}", orderId);
    }

    public OrderViewResponseDto findById(final UUID orderId) {
        return orderViewRepository.findById(orderId.toString())
                .map(OrderViewResponseDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order view not found", ErrorCode.RESOURCE_NOT_FOUND_ORDER_VIEW));
    }

    private OrderView toOrderView(final OrderProjectionMessage message) {
        final List<OrderViewItem> items = message.items().stream().map(OrderProjectionService::toViewItem).toList();
        return OrderView.builder()
                .id(message.orderId().toString())
                .customerId(message.customerId())
                .status(message.status())
                .items(items)
                .totalAmount(message.totalAmount())
                .updatedAt(message.updatedAt())
                .build();
    }

    private static OrderViewItem toViewItem(final OrderProjectionItem item) {
        return OrderViewItem.builder()
                .id(item.id())
                .description(item.description())
                .unitPrice(item.unitPrice())
                .quantity(item.quantity())
                .subtotal(item.subtotal())
                .build();
    }
}
