package br.com.ronybrand.orderapi.commons.filter;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import java.util.Arrays;

/**
 * The nine filter operators supported by the shared search engine, encoded as a string prefix in
 * client filter values ({@code "eq:value"}, {@code "between:1,10"}...).
 */
public enum Operator {

    EQ("eq"),
    NEQ("neq"),
    LT("lt"),
    LTE("lte"),
    GT("gt"),
    GTE("gte"),
    IN("in"),
    BETWEEN("between"),
    LK("lk");

    private final String value;

    Operator(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Operator fromValue(final String value) {
        return Arrays.stream(values())
                .filter(operator -> operator.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("Unknown filter operator: " + value,
                        ErrorCode.VALIDATION_INVALID_FILTER_VALUE));
    }
}
