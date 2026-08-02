package br.com.ronybrand.orderapi.commons.filter;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * Implemented by every domain service that exposes a {@code /search} endpoint. {@code filters}
 * is the normalized map built by {@link SearchUtils#buildFiltersFromQueryParams}.
 */
public interface SearchService<T> {

    Page<T> search(Map<String, List<String>> filters, String order, int page, int size);
}
