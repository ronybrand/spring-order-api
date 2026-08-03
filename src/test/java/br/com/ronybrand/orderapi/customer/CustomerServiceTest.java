package br.com.ronybrand.orderapi.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ronybrand.orderapi.commons.config.PaginationProperties;
import br.com.ronybrand.orderapi.commons.exception.ConflictException;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.order.OrderRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;

class CustomerServiceTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PaginationProperties paginationProperties = new PaginationProperties(0, 20, 100);
    @SuppressWarnings("unchecked")
    private final AuditorAware<String> auditorAware = mock(AuditorAware.class);
    private final CustomerService service =
            new CustomerService(customerRepository, orderRepository, entityManager, paginationProperties, auditorAware);

    @Test
    void create_ShouldPersistCustomer_WhenDataIsValid() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-12345", "AB123456", "ada@example.com");
        when(customerRepository.existsByTaxId("TAX-12345")).thenReturn(false);
        when(customerRepository.existsByPassportNumber("AB123456")).thenReturn(false);
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    final Customer customer = invocation.getArgument(0);
                    customer.setId(UUID.randomUUID());
                    return customer;
                });

        service.create(request);

        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void create_ShouldThrowConflictException_WhenTaxIdAlreadyExists() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-12345", null, "ada@example.com");
        when(customerRepository.existsByTaxId("TAX-12345")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrowConflictException_WhenPassportNumberAlreadyExists() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-12345", "AB123456", "ada@example.com");
        when(customerRepository.existsByTaxId("TAX-12345")).thenReturn(false);
        when(customerRepository.existsByPassportNumber("AB123456")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_CUSTOMER_PASSPORT_EXISTS));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_ShouldSucceed_WhenPassportNumberIsAbsent() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-12345", null, "ada@example.com");
        when(customerRepository.existsByTaxId("TAX-12345")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        verify(customerRepository, never()).existsByPassportNumber(any());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void findById_ShouldReturnCustomerDto_WhenExists() {
        final UUID id = UUID.randomUUID();
        final Customer customer = Customer.builder().id(id).name("Ada Lovelace").taxId("TAX-12345").email("ada@example.com").build();
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        final CustomerDto result = service.findById(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Ada Lovelace");
    }

    @Test
    void findById_ShouldThrowResourceNotFoundException_WhenNotExists() {
        final UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND_CUSTOMER));
    }

    @Test
    void update_ShouldPersistChanges_WhenDataIsValid() {
        final UUID id = UUID.randomUUID();
        final Customer existing = Customer.builder().id(id).name("Old Name").taxId("TAX-OLD").email("old@example.com").build();
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-NEW", null, "new@example.com");
        when(customerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByTaxIdAndIdNot("TAX-NEW", id)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(id, request);

        verify(customerRepository).save(existing);
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getTaxId()).isEqualTo("TAX-NEW");
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void update_ShouldThrowResourceNotFoundException_WhenNotExists() {
        final UUID id = UUID.randomUUID();
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-NEW", null, "new@example.com");
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, request)).isInstanceOf(ResourceNotFoundException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void update_ShouldThrowConflictException_WhenTaxIdBelongsToAnotherCustomer() {
        final UUID id = UUID.randomUUID();
        final Customer existing = Customer.builder().id(id).name("Old Name").taxId("TAX-OLD").email("old@example.com").build();
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-NEW", null, "new@example.com");
        when(customerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByTaxIdAndIdNot("TAX-NEW", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void update_ShouldThrowConflictException_WhenPassportNumberBelongsToAnotherCustomer() {
        final UUID id = UUID.randomUUID();
        final Customer existing = Customer.builder().id(id).name("Old Name").taxId("TAX-OLD").email("old@example.com").build();
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-OLD", "AB123456", "new@example.com");
        when(customerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByTaxIdAndIdNot("TAX-OLD", id)).thenReturn(false);
        when(customerRepository.existsByPassportNumberAndIdNot("AB123456", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_CUSTOMER_PASSPORT_EXISTS));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void update_ShouldSucceed_WhenTaxIdBelongsToSelf() {
        final UUID id = UUID.randomUUID();
        final Customer existing = Customer.builder().id(id).name("Old Name").taxId("TAX-SAME").email("old@example.com").build();
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-SAME", null, "new@example.com");
        when(customerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByTaxIdAndIdNot("TAX-SAME", id)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(id, request);

        verify(customerRepository).save(existing);
    }

    @Test
    void delete_ShouldSoftDeleteCustomer_WhenExists() {
        final UUID id = UUID.randomUUID();
        final Customer existing = Customer.builder().id(id).name("Ada Lovelace").taxId("TAX-12345").email("ada@example.com").build();
        when(customerRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("admin-user"));

        service.delete(id);

        assertThat(existing.getDeletedAt()).isNotNull();
        assertThat(existing.getDeletedBy()).isEqualTo("admin-user");
        verify(customerRepository).save(existing);
    }

    @Test
    void delete_ShouldThrowResourceNotFoundException_WhenNotExists() {
        final UUID id = UUID.randomUUID();
        when(customerRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void delete_ShouldThrowInvalidInputException_WhenCustomerHasActiveOrders() {
        final UUID id = UUID.randomUUID();
        final Customer existing = Customer.builder().id(id).name("Ada Lovelace").taxId("TAX-12345").email("ada@example.com").build();
        when(customerRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByCustomerId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_CUSTOMER_HAS_ORDERS));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void delete_ShouldSucceed_WhenCustomerHasNoActiveOrders() {
        final UUID id = UUID.randomUUID();
        final Customer existing = Customer.builder().id(id).name("Ada Lovelace").taxId("TAX-12345").email("ada@example.com").build();
        when(customerRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByCustomerId(id)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("admin-user"));

        service.delete(id);

        verify(customerRepository).save(existing);
    }
}
