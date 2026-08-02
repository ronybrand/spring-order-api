package br.com.ronybrand.orderapi.order;

import br.com.ronybrand.orderapi.commons.filter.SearchSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecification {

    private OrderSpecification() {
    }

    public static Specification<Order> byCriteria(final Map<String, List<String>> filters) {
        return SearchSpecification.byCriteria(filters);
    }
}
