package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Locks down the wire format {@link RawJsonObjectMapperFactory#create()} produces for
 * {@link LocalDateTime} fields: it must be the ISO-8601 string the message contracts document
 * (see the {@code notification}/{@code order.readmodel} Pact tests), not Jackson's
 * numeric-array default - the exact drift a red-team review of the transactional outbox surfaced,
 * since {@code OutboxService} uses this factory to build the payload {@code OutboxPublisher} ships
 * to RabbitMQ verbatim.
 */
class RawJsonObjectMapperFactoryTest {

    @Test
    void create_ShouldSerializeLocalDateTime_AsIso8601String_NotANumericArray() throws Exception {
        final ObjectMapper objectMapper = RawJsonObjectMapperFactory.create();
        final LocalDateTime dateTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123456000);

        final String json = objectMapper.writeValueAsString(Map.of("t", dateTime));

        assertThat(json).contains("\"2026-01-02T03:04:05.123456\"");
    }
}
