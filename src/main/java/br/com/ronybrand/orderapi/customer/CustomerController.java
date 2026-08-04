package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.api.Constants;
import br.com.ronybrand.orderapi.commons.filter.SearchControllerSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customers")
public class CustomerController implements SearchControllerSupport<CustomerDto> {

    private final CustomerService customerService;

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ConflictException if taxId/passportNumber are already in use
     */
    @PostMapping
    @PreAuthorize(Constants.HAS_ROLE_ADMIN)
    @Operation(summary = "Create a new customer", operationId = "createCustomer")
    public ResponseEntity<Void> create(@RequestBody @Valid final CustomerRequestDto request) {
        customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Query syntax: {@code filter[field]=value} or {@code filter[field][operator]=value}
     * ({@code eq|neq|lt|lte|gt|gte|in|between|lk}); {@code order=field} or {@code order=-field};
     * {@code page} is 0-based.
     */
    @GetMapping("/search")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Search customers", operationId = "searchCustomers")
    public ResponseEntity<Page<CustomerDto>> search(@RequestParam final MultiValueMap<String, String> allParams,
            @RequestParam(required = false) final String order,
            @RequestParam(required = false, defaultValue = "0") final int page,
            @RequestParam(required = false, defaultValue = "20") final int size) {
        return doSearchByGetMethod(allParams, order, page, size, customerService);
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the customer does not exist
     */
    @GetMapping("/{id}")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Find a customer by id", operationId = "findCustomerById")
    public ResponseEntity<CustomerDto> findById(@PathVariable final UUID id) {
        return ResponseEntity.ok(customerService.findById(id));
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the customer does not exist
     * @throws br.com.ronybrand.orderapi.commons.exception.ConflictException if taxId/passportNumber are already in use by another customer
     */
    @PutMapping("/{id}")
    @PreAuthorize(Constants.HAS_ROLE_ADMIN)
    @Operation(summary = "Update an existing customer", operationId = "updateCustomer")
    public ResponseEntity<Void> update(@PathVariable final UUID id, @RequestBody @Valid final CustomerRequestDto request) {
        customerService.update(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the customer does not exist
     */
    @PatchMapping("/{id}/marketing-opt-in")
    @PreAuthorize(Constants.HAS_ROLE_ADMIN)
    @Operation(summary = "Update a customer's marketing opt-in flag", operationId = "updateCustomerMarketingOptIn")
    public ResponseEntity<Void> updateMarketingOptIn(@PathVariable final UUID id,
            @RequestBody @Valid final CustomerMarketingOptInUpdateRequestDto request) {
        customerService.updateMarketingOptIn(id, request.marketingOptIn());
        return ResponseEntity.noContent().build();
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the customer does not exist
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(Constants.HAS_ROLE_ADMIN)
    @Operation(summary = "Soft-delete a customer", operationId = "deleteCustomer")
    public ResponseEntity<Void> delete(@PathVariable final UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
