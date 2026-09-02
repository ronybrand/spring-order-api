package br.com.ronybrand.orderapi.notification;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import br.com.ronybrand.orderapi.order.OrderStatus;
import br.com.ronybrand.orderapi.order.OrderStatusChangedEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * Producer side of the message pact: verifies that a real {@link OrderStatusChangedEvent},
 * serialized through the very same {@link JacksonJsonMessageConverter} {@code RabbitTemplate}
 * uses in production (Jackson 3, not the classic Jackson 2 {@code ObjectMapper} that
 * {@link OrderNotificationRabbitListener} happens to use for reading), satisfies the contract the
 * consumer side (see {@link OrderStatusMessagePactConsumerTest}) declared it needs. Building the
 * message any other way (e.g. a hand-rolled Jackson 2 {@code ObjectMapper}) would test against a
 * simulation of the wire format, not the wire format itself.
 */
@Provider("spring-order-api-order-status-producer")
@PactFolder("target/pacts")
class OrderStatusMessagePactProviderTest {

    private static final JacksonJsonMessageConverter MESSAGE_CONVERTER = new JacksonJsonMessageConverter();

    @BeforeEach
    void before(final PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(final PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("an order status changed event")
    String verifyOrderStatusChangedEvent() {
        final OrderStatusChangedEvent event = new OrderStatusChangedEvent(UUID.randomUUID(), "ada@example.com",
                "Ada Lovelace", OrderStatus.OPEN, OrderStatus.CONFIRMED, new BigDecimal("10.00"),
                LocalDateTime.now(ZoneOffset.UTC));
        final byte[] body = MESSAGE_CONVERTER.toMessage(event, new MessageProperties()).getBody();
        return new String(body, StandardCharsets.UTF_8);
    }
}
