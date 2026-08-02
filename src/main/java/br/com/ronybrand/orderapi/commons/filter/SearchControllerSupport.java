package br.com.ronybrand.orderapi.commons.filter;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

/**
 * Mixed into a controller to wire {@code GET /search} in one line, reusing the shared filter
 * parsing instead of duplicating it per domain.
 */
public interface SearchControllerSupport<T> {

    default ResponseEntity<Page<T>> doSearchByGetMethod(final MultiValueMap<String, String> queryParams, final String order,
            final int page, final int size, final SearchService<T> searchService) {
        final Map<String, List<String>> filters = SearchUtils.buildFiltersFromQueryParams(queryParams);
        return ResponseEntity.ok(searchService.search(filters, order, page, size));
    }
}
