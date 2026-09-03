package br.com.ronybrand.orderapi.commons.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Stable catalog of domain error codes (DOMAIN.md §6). The Java constant name is descriptive;
 * {@link #code} is the stable identifier exposed to the client - never reuse an existing code for
 * a different meaning, add a new entry instead.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_MISSING_FIELD("VALIDATION-01", "Missing required field"),
    VALIDATION_CUSTOMER_TAXID_EXISTS("VALIDATION-02", "Tax ID already registered for another customer"),
    VALIDATION_CUSTOMER_PASSPORT_EXISTS("VALIDATION-03", "Passport number already registered for another customer"),
    VALIDATION_CUSTOMER_HAS_ORDERS("VALIDATION-04", "Customer has associated orders and cannot be deleted"),
    VALIDATION_INVALID_CUSTOMER_ID("VALIDATION-05", "customerId does not exist"),
    VALIDATION_ORDER_NOT_EDITABLE("VALIDATION-06", "Order is not in an editable status"),
    VALIDATION_ORDER_EMPTY("VALIDATION-07", "Order has no items"),
    VALIDATION_ORDER_INVALID_STATUS_TRANSITION("VALIDATION-08", "Invalid status transition"),
    VALIDATION_INVALID_FILTER_VALUE("VALIDATION-09", "Invalid filter value"),
    VALIDATION_INVALID_SORT_FIELD("VALIDATION-10", "Invalid sort field"),
    VALIDATION_CONSTRAINT_VIOLATION("VALIDATION-11", "Field validation violation"),

    RESOURCE_NOT_FOUND_CUSTOMER("RESOURCE-NOT-FOUND-01", "Customer not found"),
    RESOURCE_NOT_FOUND_ORDER("RESOURCE-NOT-FOUND-02", "Order not found"),
    RESOURCE_NOT_FOUND_ITEM("RESOURCE-NOT-FOUND-03", "Item not found"),
    RESOURCE_NOT_FOUND_ORDER_VIEW("RESOURCE-NOT-FOUND-04", "Order view not found"),

    CONFLICT_CONCURRENT_MODIFICATION("CONFLICT-01", "Concurrency conflict (simultaneous modification)"),
    CONFLICT_DATA_INTEGRITY_VIOLATION("CONFLICT-02", "Data integrity violation"),

    AUTHORIZATION_ACCESS_DENIED("AUTHORIZATION-01", "Access denied"),
    INTERNAL_ERROR("INTERNAL-ERROR", "Unexpected internal error");

    private final String code;
    private final String description;
}
