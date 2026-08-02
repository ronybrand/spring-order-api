package br.com.ronybrand.orderapi.order;

/**
 * Searchable field names for {@code /orders/search} - documentation only, see
 * {@link br.com.ronybrand.orderapi.customer.CustomerConstants} for the rationale.
 */
public final class OrderConstants {

    public static final String ID = "id";
    public static final String STATUS = "status";
    public static final String TOTAL = "total";
    public static final String VERSION = "version";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private OrderConstants() {
    }
}
