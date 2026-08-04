package br.com.ronybrand.orderapi.commons.filter;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns the normalized filter map built by {@link SearchUtils} into a Spring Data JPA
 * {@link Specification}, resolving each field's Java type against the entity's own JPA metamodel
 * instead of a hand-maintained allowlist per domain. A field that doesn't exist, or that isn't a
 * simple column (e.g. a {@code @ManyToOne}/{@code @OneToMany} association), is silently ignored -
 * treated as if the client hadn't sent that filter at all.
 */
public final class SearchSpecification {

    private static final int BETWEEN_PARTS_COUNT = 2;

    private SearchSpecification() {
    }

    public static <T> Specification<T> byCriteria(final Map<String, List<String>> filters) {
        return (root, query, cb) -> {
            final List<Predicate> andPredicates = filters.entrySet().stream()
                    .map(entry -> toFieldPredicate(cb, root, entry.getKey(), entry.getValue()))
                    .filter(Objects::nonNull)
                    .toList();
            return cb.and(andPredicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate toFieldPredicate(final CriteriaBuilder cb, final Root<?> root, final String field,
            final List<String> rawFilterValues) {
        final Class<?> attributeType = resolveFilterableType(root, field);
        if (attributeType == null) {
            return null;
        }
        final List<Predicate> orPredicates = rawFilterValues.stream()
                .map(rawFilterValue -> toValuePredicate(cb, root, field, attributeType, rawFilterValue))
                .toList();
        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    private static Class<?> resolveFilterableType(final Root<?> root, final String field) {
        try {
            final Attribute<?, ?> attribute = root.getModel().getAttribute(field);
            if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC) {
                return null;
            }
            return attribute.getJavaType();
        } catch (final IllegalArgumentException _) {
            return null;
        }
    }

    private static Predicate toValuePredicate(final CriteriaBuilder cb, final Root<?> root, final String field,
            final Class<?> attributeType, final String rawFilterValue) {
        final int separatorIndex = rawFilterValue.indexOf(':');
        final Operator operator = Operator.fromValue(rawFilterValue.substring(0, separatorIndex));
        final String rawValue = rawFilterValue.substring(separatorIndex + 1);
        final Path<Object> path = root.get(field);

        return switch (operator) {
            case EQ -> cb.equal(path, FilterValueParser.parse(rawValue, attributeType));
            case NEQ -> cb.notEqual(path, FilterValueParser.parse(rawValue, attributeType));
            case LT, LTE, GT, GTE -> comparisonPredicate(cb, path, attributeType, operator, rawValue);
            case IN -> path.in(FilterValueParser.parseList(rawValue, attributeType));
            case BETWEEN -> betweenPredicate(cb, path, attributeType, rawValue);
            case LK -> likePredicate(cb, path, attributeType, rawValue);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate comparisonPredicate(final CriteriaBuilder cb, final Path<Object> path,
            final Class<?> attributeType, final Operator operator, final String rawValue) {
        if (attributeType.isEnum()) {
            throw new InvalidInputException("Operator " + operator.getValue() + " is not supported for enum fields",
                    ErrorCode.VALIDATION_INVALID_FILTER_VALUE);
        }
        final Comparable value = (Comparable) FilterValueParser.parse(rawValue, attributeType);
        final Path<Comparable> comparablePath = (Path) path;
        return switch (operator) {
            case LT -> cb.lessThan(comparablePath, value);
            case LTE -> cb.lessThanOrEqualTo(comparablePath, value);
            case GT -> cb.greaterThan(comparablePath, value);
            case GTE -> cb.greaterThanOrEqualTo(comparablePath, value);
            default -> throw new IllegalStateException("Not a comparison operator: " + operator);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate betweenPredicate(final CriteriaBuilder cb, final Path<Object> path,
            final Class<?> attributeType, final String rawValue) {
        final String[] parts = rawValue.split(",", BETWEEN_PARTS_COUNT);
        if (parts.length != BETWEEN_PARTS_COUNT) {
            throw new InvalidInputException("Operator between requires two comma-separated values",
                    ErrorCode.VALIDATION_INVALID_FILTER_VALUE);
        }
        final Comparable lower = (Comparable) FilterValueParser.parse(parts[0].trim(), attributeType);
        final Comparable upper = (Comparable) FilterValueParser.parse(parts[1].trim(), attributeType);
        final Path<Comparable> comparablePath = (Path) path;
        return cb.between(comparablePath, lower, upper);
    }

    private static Predicate likePredicate(final CriteriaBuilder cb, final Path<Object> path, final Class<?> attributeType,
            final String rawValue) {
        if (!String.class.equals(attributeType)) {
            throw new InvalidInputException("Operator lk is only supported for text fields",
                    ErrorCode.VALIDATION_INVALID_FILTER_VALUE);
        }
        @SuppressWarnings("unchecked")
        final Path<String> stringPath = (Path<String>) (Path<?>) path;
        final String pattern = "%" + SearchUtils.escapeLike(StringUtils.defaultString(rawValue)) + "%";
        return cb.like(cb.lower(stringPath), pattern.toLowerCase(Locale.ROOT), '\\');
    }
}
