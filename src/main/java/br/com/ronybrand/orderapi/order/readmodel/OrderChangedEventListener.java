package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.order.Item;
import br.com.ronybrand.orderapi.order.Order;
import br.com.ronybrand.orderapi.order.OrderChangedEvent;
import br.com.ronybrand.orderapi.order.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process {@link OrderChangedEvent} to RabbitMQ. Runs {@code AFTER_COMMIT}, in its
 * own new read-only transaction (the original request's transaction has already closed by then),
 * so {@link OrderRepository#findByIdWithItems} can load {@code customer}/{@code items} (both lazy)
 * without a {@code LazyInitializationException}. Publish failures are logged, not propagated -
 * same rationale as {@code OrderStatusEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderChangedEventListener {

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onOrderChanged(final OrderChangedEvent event) {
        final Optional<Order> order = orderRepository.findByIdWithItems(event.orderId());
        if (order.isEmpty()) {
            log.warn("Order not found while building projection message, skipping: orderId={}", event.orderId());
            return;
        }
        try {
            rabbitTemplate.convertAndSend(OrderProjectionConfig.EXCHANGE, OrderProjectionConfig.ROUTING_KEY, toMessage(order.get()));
        } catch (final RuntimeException e) {
            log.warn("Failed to publish order projection message: orderId={}", event.orderId(), e);
        }
    }

    private OrderProjectionMessage toMessage(final Order order) {
        final List<OrderProjectionItem> items = order.getItems().stream().map(OrderChangedEventListener::toItem).toList();
        return new OrderProjectionMessage(order.getId(), order.getCustomer().getId(), order.getStatus(), items,
                order.getTotal(), order.getUpdatedAt());
    }

    private static OrderProjectionItem toItem(final Item item) {
        final BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new OrderProjectionItem(item.getId(), item.getDescription(), item.getUnitPrice(), item.getQuantity(), subtotal);
    }
}
