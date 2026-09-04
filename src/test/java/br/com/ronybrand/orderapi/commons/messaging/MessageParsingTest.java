package br.com.ronybrand.orderapi.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessageParsingTest {

    private static final class MalformedTestMessageException extends RuntimeException {
        MalformedTestMessageException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private record Sample(String id, String name) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Sample parse(final byte[] body) {
        return MessageParsing.parseOrThrow(body, objectMapper, Sample.class,
                s -> s == null || s.id() == null || s.name() == null,
                "Malformed sample message", "Sample message is missing required fields",
                MalformedTestMessageException::new);
    }

    @Test
    void parseOrThrow_ShouldReturnValue_WhenJsonIsValidAndComplete() {
        final Sample result = parse("{\"id\":\"1\",\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo(new Sample("1", "Ada"));
    }

    @Test
    void parseOrThrow_ShouldThrowWithMalformedMessage_WhenJsonIsInvalid() {
        assertThatThrownBy(() -> parse("not-json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MalformedTestMessageException.class)
                .hasMessage("Malformed sample message");
    }

    @Test
    void parseOrThrow_ShouldThrowWithMissingFieldsMessage_WhenRequiredFieldIsAbsent() {
        assertThatThrownBy(() -> parse("{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MalformedTestMessageException.class)
                .hasMessage("Sample message is missing required fields");
    }
}
