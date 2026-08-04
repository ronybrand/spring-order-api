package br.com.ronybrand.orderapi.commons.filter;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

/**
 * Pure structural parsing for the shared search engine - no DB access except for sort field
 * validation against the entity's own JPA metamodel.
 */
public final class SearchUtils {

    private static final Pattern FILTER_PARAM_PATTERN = Pattern.compile("^filter\\[([^]]+)](?:\\[([^]]+)])?$");
    private static final String ID_FIELD = "id";

    private SearchUtils() {
    }

    /**
     * Parses {@code filter[field]=value} / {@code filter[field][op]=value} query params into a
     * normalized map of {@code "op:value"} strings per field. An operator-less filter defaults to
     * {@code eq}; repeated values for the same field/operator are collected (later ORed together
     * by {@link SearchSpecification}).
     */
    public static Map<String, List<String>> buildFiltersFromQueryParams(final MultiValueMap<String, String> queryParams) {
        final Map<String, List<String>> filters = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            final Matcher matcher = FILTER_PARAM_PATTERN.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            final String field = matcher.group(1);
            final String operator = matcher.group(2);
            final String prefix = (operator == null ? Operator.EQ.getValue() : operator) + ":";
            final List<String> values = filters.computeIfAbsent(field, key -> new ArrayList<>());
            for (final String rawValue : entry.getValue()) {
                values.add(prefix + rawValue);
            }
        }
        return filters;
    }

    /**
     * {@code order=field} (ascending) or {@code order=-field} (descending). Always appends
     * {@code id} as a stable tiebreaker unless already sorting by it, so pagination stays
     * consistent across pages when the primary sort field has duplicate values.
     */
    public static Sort buildSort(final String order, final Metamodel metamodel, final Class<?> entityClass) {
        if (StringUtils.isBlank(order)) {
            return Sort.by(Sort.Direction.ASC, ID_FIELD);
        }
        final boolean descending = order.startsWith("-");
        final String field = descending ? order.substring(1) : order;
        validateSortField(field, metamodel, entityClass);

        final Sort.Order primary = new Sort.Order(descending ? Sort.Direction.DESC : Sort.Direction.ASC, field);
        return ID_FIELD.equals(field) ? Sort.by(primary) : Sort.by(primary, Sort.Order.asc(ID_FIELD));
    }

    private static void validateSortField(final String field, final Metamodel metamodel, final Class<?> entityClass) {
        try {
            final EntityType<?> entityType = metamodel.entity(entityClass);
            final Attribute<?, ?> attribute = entityType.getAttribute(field);
            if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC) {
                throw new InvalidInputException("Invalid sort field: " + field, ErrorCode.VALIDATION_INVALID_SORT_FIELD);
            }
        } catch (final IllegalArgumentException e) {
            final InvalidInputException invalidInputException =
                    new InvalidInputException("Invalid sort field: " + field, ErrorCode.VALIDATION_INVALID_SORT_FIELD);
            invalidInputException.initCause(e);
            throw invalidInputException;
        }
    }

    public static Pageable buildPageable(final int page, final int size, final int maxSize, final Sort sort) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.min(Math.max(size, 1), maxSize);
        return PageRequest.of(safePage, safeSize, sort);
    }

    public static String escapeLike(final String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
