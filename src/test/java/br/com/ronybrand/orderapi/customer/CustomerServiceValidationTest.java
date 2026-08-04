package br.com.ronybrand.orderapi.customer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.ronybrand.orderapi.commons.config.PaginationProperties;
import br.com.ronybrand.orderapi.order.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Proves {@code @NotNull} on {@link CustomerService} method parameters is actually enforced -
 * {@code @Validated} on the class only takes effect through the AOP proxy Spring builds around it
 * (never on a plain {@code new CustomerService(...)}, which is what every other unit test in this
 * package uses), so this wires that proxy explicitly instead of booting the full application.
 */
class CustomerServiceValidationTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PaginationProperties paginationProperties = new PaginationProperties(0, 20, 100);
    @SuppressWarnings("unchecked")
    private final AuditorAware<String> auditorAware = mock(AuditorAware.class);

    private CustomerService validatedProxy() {
        final CustomerService target =
                new CustomerService(customerRepository, orderRepository, entityManager, paginationProperties, auditorAware);
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        final MethodValidationPostProcessor postProcessor = new MethodValidationPostProcessor();
        postProcessor.setValidator(validator);
        postProcessor.setProxyTargetClass(true);
        postProcessor.afterPropertiesSet();
        return (CustomerService) postProcessor.postProcessAfterInitialization(target, "customerService");
    }

    @Test
    void findById_ShouldThrowConstraintViolationException_WhenIdIsNull() {
        final CustomerService proxy = validatedProxy();

        assertThatThrownBy(() -> proxy.findById(null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void delete_ShouldThrowConstraintViolationException_WhenIdIsNull() {
        final CustomerService proxy = validatedProxy();

        assertThatThrownBy(() -> proxy.delete(null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void update_ShouldThrowConstraintViolationException_WhenRequestIsNull() {
        final CustomerService proxy = validatedProxy();
        final UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> proxy.update(customerId, null)).isInstanceOf(ConstraintViolationException.class);
    }
}
