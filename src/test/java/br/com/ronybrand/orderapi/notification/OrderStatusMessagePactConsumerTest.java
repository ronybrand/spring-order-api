package br.com.ronybrand.orderapi.notification;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.MessageProperties;

/**
 * Consumer side of the message pact for the order-status-changed notification message:
 * describes, from {@link OrderNotificationRabbitListener}'s point of view, the shape it needs to
 * successfully parse and act on - then feeds a real message built from that pact straight into
 * the real listener, so the pact and the listener can never drift silently.
 */
@ExtendWith(PactConsumerTestExt.class)
class OrderStatusMessagePactConsumerTest {

    @Pact(consumer = "spring-order-api-notification-consumer", provider = "spring-order-api-order-status-producer")
    MessagePact orderStatusChangedPact(final MessagePactBuilder builder) {
        final PactDslJsonBody body = new PactDslJsonBody()
                .uuid("orderId")
                .stringType("customerEmail", "ada@example.com")
                .stringType("customerName", "Ada Lovelace")
                .stringMatcher("oldStatus", "OPEN|CONFIRMED|CANCELED", "OPEN")
                .stringMatcher("newStatus", "OPEN|CONFIRMED|CANCELED", "CONFIRMED")
                .decimalType("totalAmount", 10.00)
                // ISO-8601 local date-time with a variable-length fractional-second component, as
                // produced by the Jackson 3 JsonMapper backing JacksonJsonMessageConverter (the
                // converter actually used by production, see OrderStatusMessagePactProviderTest) -
                // not the fixed-width, seconds-only pattern a plain .datetime(...) matcher assumes.
                .stringMatcher("changedAt", "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?", "2026-01-01T10:00:00.123456");

        return builder
                .expectsToReceive("an order status changed event")
                .withContent(body)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "orderStatusChangedPact", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
    void listenerShouldParseAndActOnPactMessage(final List<Message> messages) {
        final EmailService emailService = mock(EmailService.class);
        final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        final OrderNotificationRabbitListener listener = new OrderNotificationRabbitListener(emailService, objectMapper);

        final byte[] body = messages.get(0).contentsAsBytes();
        listener.onMessage(new org.springframework.amqp.core.Message(body, new MessageProperties()));

        verify(emailService, times(1)).sendOrderStatusEmail(any());
    }
}
