package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.order.OrderDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process {@link OrderDeletedEvent} to RabbitMQ - counterpart to
 * {@link OrderChangedEventListener}, but with no order to re-fetch: the order is already
 * soft-deleted and {@code @SQLRestriction} hides it from every query, so the id alone is the
 * whole message. Runs {@code AFTER_COMMIT} like {@link OrderChangedEventListener}, though it needs
 * no transaction of its own since it never touches the database. Publish failures are logged at
 * {@code ERROR}, not propagated - same rationale as {@link OrderChangedEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDeletedEventListener {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDeleted(final OrderDeletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(OrderProjectionConfig.EXCHANGE, OrderProjectionConfig.DELETE_ROUTING_KEY,
                    new OrderDeletionMessage(event.orderId()));
        } catch (final RuntimeException e) {
            log.error("Order deletion projection update permanently lost, publish failed and this event is never retried: orderId={}",
                    event.orderId(), e);
        }
    }
}
