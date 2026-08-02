package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.config.CacheConfig;
import br.com.ronybrand.orderapi.commons.config.PaginationProperties;
import br.com.ronybrand.orderapi.commons.exception.ConflictException;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.commons.filter.SearchService;
import br.com.ronybrand.orderapi.commons.filter.SearchUtils;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService implements SearchService<CustomerDto> {

    private static final String SYSTEM_USER = "system";

    private final CustomerRepository customerRepository;
    private final EntityManager entityManager;
    private final PaginationProperties paginationProperties;
    private final AuditorAware<String> auditorAware;

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

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CUSTOMERS_CACHE, key = "#id")
    void delete(@NotNull final UUID id) {
        final Customer customer = findByIdOrThrow(id);
        customer.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        customer.setDeletedBy(auditorAware.getCurrentAuditor().orElse(SYSTEM_USER));
        customerRepository.save(customer);
        log.info("Customer deleted: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerDto> search(final Map<String, List<String>> filters, final String order, final int page, final int size) {
        final Sort sort = SearchUtils.buildSort(order, entityManager.getMetamodel(), Customer.class);
        final Pageable pageable = SearchUtils.buildPageable(page, size, paginationProperties.maxSize(), sort);
        return customerRepository.findAll(CustomerSpecification.byCriteria(filters), pageable).map(CustomerDto::from);
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
