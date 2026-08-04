package br.com.ronybrand.orderapi.commons.filter;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Converts a raw filter string into the Java type of the target entity attribute. Any parsing
 * failure becomes a 400 ({@link ErrorCode#VALIDATION_INVALID_FILTER_VALUE}) - the coercion
 * happens here, before a {@code Predicate} is built, instead of letting the JDBC driver blow up
 * with a generic type error.
 */
public final class FilterValueParser {

    private static final String TRUE_LITERAL = "true";
    private static final String FALSE_LITERAL = "false";

    private static final Map<Class<?>, Function<String, Object>> PARSERS = Map.ofEntries(
            Map.entry(String.class, v -> v),
            Map.entry(Integer.class, Integer::valueOf),
            Map.entry(int.class, Integer::valueOf),
            Map.entry(Long.class, Long::valueOf),
            Map.entry(long.class, Long::valueOf),
            Map.entry(BigDecimal.class, BigDecimal::new),
            Map.entry(UUID.class, UUID::fromString),
            Map.entry(LocalDate.class, LocalDate::parse),
            Map.entry(LocalDateTime.class, LocalDateTime::parse),
            Map.entry(Boolean.class, FilterValueParser::parseStrictBoolean),
            Map.entry(boolean.class, FilterValueParser::parseStrictBoolean));

    private FilterValueParser() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T parse(final String rawValue, final Class<T> targetType) {
        try {
            if (targetType.isEnum()) {
                return (T) parseEnum(rawValue, (Class<? extends Enum>) targetType);
            }
            final Function<String, Object> parser = PARSERS.get(targetType);
            if (parser == null) {
                throw new InvalidInputException("Unsupported filter field type: " + targetType.getSimpleName(),
                        ErrorCode.VALIDATION_INVALID_FILTER_VALUE);
            }
            return (T) parser.apply(rawValue);
        } catch (final IllegalArgumentException | DateTimeParseException e) {
            final InvalidInputException invalidInputException = new InvalidInputException(
                    "Invalid filter value '" + rawValue + "' for type " + targetType.getSimpleName(),
                    ErrorCode.VALIDATION_INVALID_FILTER_VALUE);
            invalidInputException.initCause(e);
            throw invalidInputException;
        }
    }

    public static <T> List<T> parseList(final String rawValue, final Class<T> targetType) {
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .map(value -> parse(value, targetType))
                .toList();
    }

    private static Boolean parseStrictBoolean(final String rawValue) {
        if (TRUE_LITERAL.equalsIgnoreCase(rawValue)) {
            return Boolean.TRUE;
        }
        if (FALSE_LITERAL.equalsIgnoreCase(rawValue)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Not a boolean: " + rawValue);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T parseEnum(final String rawValue, final Class<?> enumType) {
        for (final Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(rawValue)) {
                return (T) constant;
            }
        }
        throw new IllegalArgumentException("No enum constant matches '" + rawValue + "'");
    }
}
