package br.com.ronybrand.orderapi.commons.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class SearchUtilsTest {

    @Test
    void buildFiltersFromQueryParams_ShouldDefaultToEq_WhenNoOperatorGiven() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter[taxId]", "TAX-1");
        params.add("order", "-createdAt");

        final Map<String, List<String>> filters = SearchUtils.buildFiltersFromQueryParams(params);

        assertThat(filters).containsOnlyKeys("taxId");
        assertThat(filters.get("taxId")).containsExactly("eq:TAX-1");
    }

    @Test
    void buildFiltersFromQueryParams_ShouldParseExplicitOperator() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter[name][lk]", "Ada");

        final Map<String, List<String>> filters = SearchUtils.buildFiltersFromQueryParams(params);

        assertThat(filters.get("name")).containsExactly("lk:Ada");
    }

    @Test
    void buildFiltersFromQueryParams_ShouldCollectRepeatedValues_ForSameField() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter[status]", "OPEN");
        params.add("filter[status]", "CONFIRMED");

        final Map<String, List<String>> filters = SearchUtils.buildFiltersFromQueryParams(params);

        assertThat(filters.get("status")).containsExactly("eq:OPEN", "eq:CONFIRMED");
    }

    @Test
    void buildPageable_ShouldClampSizeToMax() {
        final Pageable pageable = SearchUtils.buildPageable(0, 500, 100, Sort.by("id"));

        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    void buildPageable_ShouldClampSizeToAtLeastOne() {
        final Pageable pageable = SearchUtils.buildPageable(0, 0, 100, Sort.by("id"));

        assertThat(pageable.getPageSize()).isEqualTo(1);
    }

    @Test
    void buildPageable_ShouldClampNegativePageToZero() {
        final Pageable pageable = SearchUtils.buildPageable(-5, 20, 100, Sort.by("id"));

        assertThat(pageable.getPageNumber()).isZero();
    }

    @Test
    void escapeLike_ShouldEscapeSqlWildcards() {
        assertThat(SearchUtils.escapeLike("100%_off")).isEqualTo("100\\%\\_off");
    }
}
