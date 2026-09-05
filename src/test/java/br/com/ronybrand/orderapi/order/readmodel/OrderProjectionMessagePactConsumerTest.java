package br.com.ronybrand.orderapi.order.readmodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import br.com.ronybrand.orderapi.commons.messaging.MessagingMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.MessageProperties;

/**
 * Consumer side of the message pact for the order-projection message (the {@code OrderProjectionMessage}
 * written to the outbox by {@code OrderService}, consumed by {@link OrderProjectionRabbitListener}) -
 * same rationale as {@code notification.OrderStatusMessagePactConsumerTest} (see
 * <a href="../../../../../../../../../docs/adr/0005-message-pact-without-broker.md">ADR 0005</a>):
 * describes, from the listener's point of view, the shape it needs to successfully parse, then feeds
 * a real message built from that pact straight into the real listener, so the pact and the listener
 * can never drift silently.
 */
@ExtendWith(PactConsumerTestExt.class)
class OrderProjectionMessagePactConsumerTest {

    @Pact(consumer = "spring-order-api-projection-consumer", provider = "spring-order-api-order-changed-producer")
    MessagePact orderProjectionPact(final MessagePactBuilder builder) {
        final PactDslJsonBody item = new PactDslJsonBody()
                .uuid("id")
                .stringType("description", "Widget")
                .decimalType("unitPrice", 10.00)
                .integerType("quantity", 2)
                .decimalType("subtotal", 20.00);

        final PactDslJsonBody body = new PactDslJsonBody()
                .uuid("orderId")
                .uuid("customerId")
                .stringMatcher("status", "OPEN|CONFIRMED|CANCELED", "CONFIRMED")
                .minArrayLike("items", 1, item)
                .decimalType("totalAmount", 20.00)
                // same variable-length fractional-second ISO-8601 shape as
                // notification.OrderStatusMessagePactConsumerTest#orderStatusChangedPact - both
                // messages are produced by the same Jackson-3-backed RabbitTemplate.
                .stringMatcher("updatedAt", "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?", "2026-01-01T10:00:00.123456");

        return builder
                .expectsToReceive("an order projection message")
                .withContent(body)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "orderProjectionPact", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
    void listenerShouldParseAndActOnPactMessage(final List<Message> messages) {
        final OrderProjectionService orderProjectionService = mock(OrderProjectionService.class);
        final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        final OrderProjectionRabbitListener listener = new OrderProjectionRabbitListener(orderProjectionService, objectMapper,
            new MessagingMetrics(new SimpleMeterRegistry()));

        final byte[] body = messages.get(0).contentsAsBytes();
        listener.onMessage(new org.springframework.amqp.core.Message(body, new MessageProperties()));

        verify(orderProjectionService, times(1)).upsert(any());
    }
}
