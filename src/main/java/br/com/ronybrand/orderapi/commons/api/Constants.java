package br.com.ronybrand.orderapi.commons.api;

/**
 * SpEL (Spring Expression Language) constants used in {@code @PreAuthorize} - never write
 * {@code hasRole('...')} inline in a controller, always reuse these, so the authorization policy
 * stays auditable in a single place.
 */
public final class Constants {

    public static final String HAS_ROLE_ADMIN = "hasRole('ADMIN')";
    public static final String HAS_ROLE_USER = "hasRole('USER')";

    private Constants() {
    }
}
