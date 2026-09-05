package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.ronybrand.orderapi.commons.config.PaginationProperties;
import br.com.ronybrand.orderapi.commons.messaging.OutboxService;
import br.com.ronybrand.orderapi.customer.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Proves {@code @NotNull} on {@link OrderService} method parameters is actually enforced -
 * {@code @Validated} on the class only takes effect through the AOP proxy Spring builds around it
 * (never on a plain {@code new OrderService(...)}, which is what every other unit test in this
 * package uses), so this wires that proxy explicitly, the same way Spring Boot's
 * {@code ValidationAutoConfiguration} does (CGLIB / target-class, per {@code spring.aop.proxy-target-class}
 * defaulting to {@code true}), instead of booting the full application.
 */
class OrderServiceValidationTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    @SuppressWarnings("unchecked")
    private final AuditorAware<String> auditorAware = mock(AuditorAware.class);
    private final OutboxService outboxService = mock(OutboxService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PaginationProperties paginationProperties = new PaginationProperties(0, 20, 100);

    private OrderService validatedProxy() {
        final OrderService target =
                new OrderService(orderRepository, customerRepository, auditorAware, outboxService, entityManager, paginationProperties);
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        final MethodValidationPostProcessor postProcessor = new MethodValidationPostProcessor();
        postProcessor.setValidator(validator);
        postProcessor.setProxyTargetClass(true);
        postProcessor.afterPropertiesSet();
        return (OrderService) postProcessor.postProcessAfterInitialization(target, "orderService");
    }

    @Test
    void findById_ShouldThrowConstraintViolationException_WhenIdIsNull() {
        final OrderService proxy = validatedProxy();

        assertThatThrownBy(() -> proxy.findById(null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void create_ShouldThrowConstraintViolationException_WhenCustomerIdIsNull() {
        final OrderService proxy = validatedProxy();
        final List<ItemRequestDto> noItems = List.of();

        assertThatThrownBy(() -> proxy.create(null, noItems)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void confirm_ShouldThrowConstraintViolationException_WhenIdIsNull() {
        final OrderService proxy = validatedProxy();

        assertThatThrownBy(() -> proxy.confirm(null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void updateItemQuantity_ShouldThrowConstraintViolationException_WhenQuantityIsNull() {
        final OrderService proxy = validatedProxy();
        final UUID orderId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        assertThatThrownBy(() -> proxy.updateItemQuantity(orderId, itemId, null))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
