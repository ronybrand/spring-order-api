package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.customer.CustomerRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Real Postgres integration for {@link OrderRepository#findByIdWithItems} - a
 * {@code left join fetch} on the to-many {@code items} association returns one duplicated row per
 * item when the order has more than one, which breaks the method's single-result {@code Optional}
 * return unless the query is {@code distinct}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class OrderRepositoryIT extends AbstractAuthIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ItemTestCleanupRepository itemTestCleanupRepository;

    @Autowired
    private OrderTestCleanupRepository orderTestCleanupRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        itemTestCleanupRepository.deleteAllHard();
        orderTestCleanupRepository.deleteAllHard();
        customerRepository.deleteAll();
        customer = customerRepository.save(
                Customer.builder().name("Ada Lovelace")
                        .taxId("TAX-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                        .email("ada@example.com")
                        .build());
    }

    @Test
    void findByIdWithItems_ShouldReturnOrder_WhenOrderHasMultipleItems() {
        Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());
        order.getItems().add(Item.builder().order(order).description("Widget").unitPrice(new BigDecimal("10.00")).quantity(1).build());
        order.getItems().add(Item.builder().order(order).description("Gadget").unitPrice(new BigDecimal("5.00")).quantity(2).build());
        order.calculateTotal();
        order = orderRepository.save(order);

        final Order found = orderRepository.findByIdWithItems(order.getId()).orElseThrow();

        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getCustomer().getId()).isEqualTo(customer.getId());
    }

    @Test
    void findByIdWithItems_ShouldReturnOrder_WhenOrderHasNoItems() {
        final Order order = orderRepository.save(Order.builder().customer(customer).status(OrderStatus.OPEN).total(BigDecimal.ZERO).build());

        final Order found = orderRepository.findByIdWithItems(order.getId()).orElseThrow();

        assertThat(found.getItems()).isEmpty();
    }
}
