package br.com.ronybrand.orderapi.commons.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Shared "parse-or-classify-as-malformed" shape every manual {@code MessageListener} in this
 * codebase runs before its retry loop ({@link RetryLoop}): not valid JSON, or valid JSON missing a
 * required field, is never retried - straight to the DLQ. Each caller keeps its own message type,
 * required-field predicate and marker exception (all listener-specific), so this only centralizes
 * the mechanical part (the try/catch and the two error-message call sites) that was previously
 * copy-pasted per listener.
 */
public final class MessageParsing {

    private MessageParsing() {
    }

    public static <T> T parseOrThrow(final byte[] body, final ObjectMapper objectMapper, final Class<T> type,
            final Predicate<T> isMissingRequiredField, final String malformedMessage,
            final String missingFieldsMessage, final BiFunction<String, Throwable, ? extends RuntimeException> exceptionFactory) {
        final T value;
        try {
            value = objectMapper.readValue(body, type);
        } catch (final IOException | RuntimeException e) {
            throw exceptionFactory.apply(malformedMessage, e);
        }
        if (isMissingRequiredField.test(value)) {
            throw exceptionFactory.apply(missingFieldsMessage, null);
        }
        return value;
    }
}
