package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.filter.SearchSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public final class CustomerSpecification {

    private CustomerSpecification() {
    }

    public static Specification<Customer> byCriteria(final Map<String, List<String>> filters) {
        return SearchSpecification.byCriteria(filters);
    }
}
