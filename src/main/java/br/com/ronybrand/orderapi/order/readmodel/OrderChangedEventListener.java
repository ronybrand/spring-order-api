package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.order.OrderChangedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process {@link OrderChangedEvent} to RabbitMQ. Runs {@code AFTER_COMMIT} - no
 * transaction or DB access of its own needed, since the event already carries the full snapshot
 * {@link OrderService} built from the same managed {@code Order} it just saved, inside the
 * original transaction. Publish is best-effort: failures are logged, not propagated, so a broker
 * hiccup here never surfaces as an error on the original request - logged at {@code ERROR} (the
 * event never left this JVM, nothing durable to retry or inspect otherwise), same rationale as
 * {@code notification.OrderStatusEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderChangedEventListener {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(final OrderChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(OrderProjectionConfig.EXCHANGE, OrderProjectionConfig.ROUTING_KEY, toMessage(event));
        } catch (final RuntimeException e) {
            log.error("Order projection update permanently lost, publish failed and this event is never retried: orderId={}",
                    event.orderId(), e);
        }
    }

    private OrderProjectionMessage toMessage(final OrderChangedEvent event) {
        final List<OrderProjectionItem> items = event.items().stream().map(OrderChangedEventListener::toItem).toList();
        return new OrderProjectionMessage(event.orderId(), event.customerId(), event.status(), items,
                event.totalAmount(), event.updatedAt());
    }

    private static OrderProjectionItem toItem(final OrderChangedEvent.ItemSnapshot item) {
        return new OrderProjectionItem(item.id(), item.description(), item.unitPrice(), item.quantity(), item.subtotal());
    }
}
