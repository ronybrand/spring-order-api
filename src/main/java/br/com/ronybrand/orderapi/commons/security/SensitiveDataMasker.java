package br.com.ronybrand.orderapi.commons.security;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Generic reflection-based {@code toString()}: any field annotated with {@link Sensitive} is
 * printed as {@value #MASK}, the rest print normally. Entities/DTOs with a sensitive field
 * delegate {@code toString()} here instead of repeating {@code @ToString.Exclude} field by
 * field - written once, covers every new class automatically.
 */
public final class SensitiveDataMasker {

    private static final String MASK = "***REDACTED***";

    private SensitiveDataMasker() {
    }

    public static String toString(final Object target) {
        final StringBuilder result = new StringBuilder(target.getClass().getSimpleName()).append('(');
        boolean first = true;
        for (final Field field : target.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!first) {
                result.append(", ");
            }
            first = false;
            result.append(field.getName()).append('=').append(renderValue(field, target));
        }
        return result.append(')').toString();
    }

    // Reading a declared field's value via reflection requires setAccessible() when the field
    // isn't public - there's no non-reflective way to implement a generic toString() otherwise.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static Object renderValue(final Field field, final Object target) {
        if (field.isAnnotationPresent(Sensitive.class)) {
            return MASK;
        }
        field.setAccessible(true); // NOSONAR java:S3011 - required to read a private field generically
        try {
            return field.get(target);
        } catch (final IllegalAccessException _) {
            return "?";
        }
    }
}
