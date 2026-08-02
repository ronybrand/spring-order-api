package br.com.ronybrand.orderapi.commons.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as sensitive data (PII/secret). The central infrastructure in {@code commons}
 * (reflection-based toString, Jackson serialization) recognizes this annotation automatically -
 * the only action needed when adding a new sensitive field is annotating it with
 * {@code @Sensitive}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
}
