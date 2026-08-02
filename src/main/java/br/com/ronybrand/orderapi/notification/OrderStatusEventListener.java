package br.com.ronybrand.orderapi.notification;

import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process {@link OrderStatusChangedEvent} to RabbitMQ, only {@code AFTER_COMMIT} -
 * a rolled-back {@code confirm}/{@code cancel} never triggers a notification. Publish failures
 * (e.g. broker unreachable) are logged, not propagated: the order-status change already committed
 * successfully, so a best-effort side effect failing here must not surface as an error on the
 * original request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusEventListener {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(final OrderStatusChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event);
        } catch (final RuntimeException e) {
            log.warn("Failed to publish order status notification: orderId={}", event.orderId(), e);
        }
    }
}
