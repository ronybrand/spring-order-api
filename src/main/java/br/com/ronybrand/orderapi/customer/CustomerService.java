package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.config.CacheConfig;
import br.com.ronybrand.orderapi.commons.exception.ConflictException;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    void create(@NotNull final CustomerRequestDto request) {
        ensureTaxIdIsUnique(request.taxId(), null);
        ensurePassportNumberIsUnique(request.passportNumber(), null);

        final Customer customer = Customer.builder()
                .name(request.name())
                .taxId(request.taxId())
                .passportNumber(request.passportNumber())
                .email(request.email())
                .build();
        final Customer saved = customerRepository.save(customer);
        log.info("Customer created: id={}", saved.getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CUSTOMERS_CACHE, key = "#id")
    CustomerDto findById(@NotNull final UUID id) {
        return CustomerDto.from(findByIdOrThrow(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CUSTOMERS_CACHE, key = "#id")
    void update(@NotNull final UUID id, @NotNull final CustomerRequestDto request) {
        final Customer customer = findByIdOrThrow(id);
        ensureTaxIdIsUnique(request.taxId(), id);
        ensurePassportNumberIsUnique(request.passportNumber(), id);

        customer.setName(request.name());
        customer.setTaxId(request.taxId());
        customer.setPassportNumber(request.passportNumber());
        customer.setEmail(request.email());
        final Customer saved = customerRepository.save(customer);
        log.info("Customer updated: id={}", saved.getId());
    }

    private Customer findByIdOrThrow(final UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found", ErrorCode.RESOURCE_NOT_FOUND_CUSTOMER));
    }

    private void ensureTaxIdIsUnique(final String taxId, final UUID excludeId) {
        final boolean exists = excludeId == null
                ? customerRepository.existsByTaxId(taxId)
                : customerRepository.existsByTaxIdAndIdNot(taxId, excludeId);
        if (exists) {
            throw new ConflictException("Tax ID already exists", ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS);
        }
    }

    private void ensurePassportNumberIsUnique(final String passportNumber, final UUID excludeId) {
        if (StringUtils.isBlank(passportNumber)) {
            return;
        }
        final boolean exists = excludeId == null
                ? customerRepository.existsByPassportNumber(passportNumber)
                : customerRepository.existsByPassportNumberAndIdNot(passportNumber, excludeId);
        if (exists) {
            throw new ConflictException("Passport number already exists", ErrorCode.VALIDATION_CUSTOMER_PASSPORT_EXISTS);
        }
    }
}
