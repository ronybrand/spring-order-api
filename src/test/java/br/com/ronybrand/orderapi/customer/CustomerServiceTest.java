package br.com.ronybrand.orderapi.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ronybrand.orderapi.commons.exception.ConflictException;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(customerRepository);

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
}
