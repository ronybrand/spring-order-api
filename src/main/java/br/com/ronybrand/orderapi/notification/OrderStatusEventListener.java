package br.com.ronybrand.orderapi.notification;

import br.com.ronybrand.orderapi.commons.messaging.MessagingMetrics;
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
 * original request. Logged at {@code ERROR}, not {@code WARN}: unlike a message already on a
 * queue (which retries and eventually DLQs, still inspectable), a failure here means the event
 * never left this JVM - there's nothing durable to retry or alert on except this log line, so it
 * needs to be loud enough to page on rather than get lost as routine noise. No outbox/durable
 * retry for this today - accepted as a rare, alertable loss rather than the added complexity of a
 * transactional outbox table + relay process for a purely technical event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusEventListener {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingMetrics messagingMetrics;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(final OrderStatusChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event);
        } catch (final RuntimeException e) {
            log.error("Order status notification permanently lost, publish failed and this event is never retried: orderId={}",
                    event.orderId(), e);
            messagingMetrics.recordEventLost("order-status");
        }
    }
}
