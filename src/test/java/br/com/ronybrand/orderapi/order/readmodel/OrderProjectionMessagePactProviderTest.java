package br.com.ronybrand.orderapi.order.readmodel;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * Producer side of the message pact: verifies that a real {@link OrderProjectionMessage},
 * serialized through the very same {@link JacksonJsonMessageConverter} {@code RabbitTemplate} uses
 * in production (Jackson 3, not the classic Jackson 2 {@code ObjectMapper} that
 * {@link OrderProjectionRabbitListener}/{@code OrderDeletionRabbitListener} happen to use for
 * reading), satisfies the contract the consumer side (see
 * {@link OrderProjectionMessagePactConsumerTest}) declared it needs - same rationale, and same
 * real mismatch this pattern already caught once, as {@code notification.OrderStatusMessagePactProviderTest}.
 */
@Provider("spring-order-api-order-changed-producer")
@PactFolder("target/pacts")
class OrderProjectionMessagePactProviderTest {

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

    @PactVerifyProvider("an order projection message")
    String verifyOrderProjectionMessage() {
        final OrderProjectionItem item = new OrderProjectionItem(UUID.randomUUID(), "Widget",
                new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        final OrderProjectionMessage message = new OrderProjectionMessage(UUID.randomUUID(), UUID.randomUUID(),
                OrderStatus.CONFIRMED, List.of(item), new BigDecimal("20.00"), LocalDateTime.now(ZoneOffset.UTC));
        final byte[] body = MESSAGE_CONVERTER.toMessage(message, new MessageProperties()).getBody();
        return new String(body, StandardCharsets.UTF_8);
    }
}
