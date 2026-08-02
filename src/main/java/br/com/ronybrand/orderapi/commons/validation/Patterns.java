package br.com.ronybrand.orderapi.commons.validation;

/**
 * Regexes shared between entity and DTO - never repeat the expression in more than one place
 * (see DOMAIN.md §2 for the origin of each pattern).
 */
public final class Patterns {

    public static final String EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String TAX_ID = "^[A-Za-z0-9./-]{5,20}$";
    public static final String PASSPORT_NUMBER = "^[A-Z0-9]{6,9}$";

    private Patterns() {
    }
}
