package br.com.ronybrand.orderapi.customer;

import br.com.ronybrand.orderapi.commons.api.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ConflictException if taxId/passportNumber are already in use
     */
    @PostMapping
    @PreAuthorize(Constants.HAS_ROLE_ADMIN)
    @Operation(summary = "Cria um novo customer", operationId = "createCustomer")
    public ResponseEntity<Void> create(@RequestBody @Valid final CustomerRequestDto request) {
        customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
