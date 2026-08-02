package br.com.ronybrand.orderapi.customer;

/**
 * Searchable field names for {@code /customers/search} - documentation only (client/Swagger
 * reference), not used as a validation allowlist; that's resolved against the JPA metamodel in
 * {@link br.com.ronybrand.orderapi.commons.filter.SearchSpecification}.
 */
public final class CustomerConstants {

    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String TAX_ID = "taxId";
    public static final String PASSPORT_NUMBER = "passportNumber";
    public static final String EMAIL = "email";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private CustomerConstants() {
    }
}
