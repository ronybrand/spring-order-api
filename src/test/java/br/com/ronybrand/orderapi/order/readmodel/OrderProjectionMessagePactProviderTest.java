package br.com.ronybrand.orderapi.order.readmodel;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import br.com.ronybrand.orderapi.commons.config.RawJsonObjectMapperFactory;
import br.com.ronybrand.orderapi.commons.messaging.OutboxService;
import br.com.ronybrand.orderapi.order.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Producer side of the message pact: verifies that a real {@link OrderProjectionMessage},
 * serialized through the very same plain Jackson 2 {@link ObjectMapper} that {@link OutboxService}
 * uses to build the outbox row's payload (see {@link RawJsonObjectMapperFactory}), satisfies the
 * contract the consumer side (see {@link OrderProjectionMessagePactConsumerTest}) declared it
 * needs.
 *
 * <p>Since ADR 0006's transactional outbox, {@code OutboxPublisher} ships that already-serialized
 * payload to RabbitMQ as raw bytes - it never runs the message through {@code RabbitTemplate}'s
 * configured {@code JacksonJsonMessageConverter} (Jackson 3) any more. Building the pact body with
 * that converter instead of {@link RawJsonObjectMapperFactory} would verify a wire format
 * production no longer produces - the same real mismatch this pattern already caught once, as
 * {@code notification.OrderStatusMessagePactProviderTest}.
 */
@Provider("spring-order-api-order-changed-producer")
@PactFolder("target/pacts")
class OrderProjectionMessagePactProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = RawJsonObjectMapperFactory.create();

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
    String verifyOrderProjectionMessage() throws JsonProcessingException {
        final OrderProjectionItem item = new OrderProjectionItem(UUID.randomUUID(), "Widget",
                new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        final OrderProjectionMessage message = new OrderProjectionMessage(UUID.randomUUID(), UUID.randomUUID(),
                OrderStatus.CONFIRMED, List.of(item), new BigDecimal("20.00"), LocalDateTime.now(ZoneOffset.UTC));
        return new String(OBJECT_MAPPER.writeValueAsBytes(message), StandardCharsets.UTF_8);
    }
}
