package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    /**
     * Skips the write if the view is already tombstoned ({@link #deleteById}) - the upsert and
     * delete queues retry independently, so a delayed upsert can arrive after the order was
     * already deleted. Since a deleted order can never be mutated again, that upsert is necessarily
     * a stale retry and applying it would resurrect a view the write side considers gone.
     */
    public void upsert(final OrderProjectionMessage message) {
        try {
            final String id = message.orderId().toString();
            final boolean tombstoned = orderViewRepository.findById(id).map(OrderView::getDeletedAt).isPresent();
            if (tombstoned) {
                log.info("Order already deleted, skipping stale projection upsert: orderId={}", message.orderId());
                return;
            }
            orderViewRepository.save(toOrderView(message));
        } catch (final DataAccessException e) {
            throw new OrderProjectionWriteException("Failed to upsert order view: orderId=" + message.orderId(), e);
        }
        log.info("Order view upserted: orderId={}", message.orderId());
    }

    /**
     * Replaces the view with a tombstone (a save, not a Mongo remove) so {@link #upsert} can detect
     * and skip a delayed upsert that arrives afterwards, instead of resurrecting it.
     */
    public void deleteById(final UUID orderId) {
        try {
            orderViewRepository.save(OrderView.builder().id(orderId.toString())
                    .deletedAt(LocalDateTime.now(ZoneOffset.UTC)).build());
        } catch (final DataAccessException e) {
            throw new OrderProjectionWriteException("Failed to delete order view: orderId=" + orderId, e);
        }
        log.info("Order view deleted: orderId={}", orderId);
    }

    public OrderViewResponseDto findById(final UUID orderId) {
        return orderViewRepository.findById(orderId.toString())
                .filter(view -> view.getDeletedAt() == null)
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
