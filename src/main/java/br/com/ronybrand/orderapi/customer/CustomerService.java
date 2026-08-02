package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.exception.ConflictException;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    void create(@NotNull final CustomerRequestDto request) {
        ensureTaxIdIsUnique(request.taxId());
        ensurePassportNumberIsUnique(request.passportNumber());

        final Customer customer = Customer.builder()
                .name(request.name())
                .taxId(request.taxId())
                .passportNumber(request.passportNumber())
                .email(request.email())
                .build();
        final Customer saved = customerRepository.save(customer);
        log.info("Customer created: id={}", saved.getId());
    }

    private void ensureTaxIdIsUnique(final String taxId) {
        if (customerRepository.existsByTaxId(taxId)) {
            throw new ConflictException("Tax ID already exists", ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS);
        }
    }

    private void ensurePassportNumberIsUnique(final String passportNumber) {
        if (StringUtils.isNotBlank(passportNumber) && customerRepository.existsByPassportNumber(passportNumber)) {
            throw new ConflictException("Passport number already exists", ErrorCode.VALIDATION_CUSTOMER_PASSPORT_EXISTS);
        }
    }
}
