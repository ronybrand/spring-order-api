package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.commons.api.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderViewController {

    private final OrderProjectionService orderProjectionService;

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if no
     *      projection exists yet for this order - may be eventual-consistency lag right after
     *      create/update, or the id genuinely doesn't exist
     */
    @GetMapping("/{id}/view")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Find an order's read-model view (MongoDB, eventual consistency)", operationId = "findOrderView")
    public ResponseEntity<OrderViewResponseDto> findView(@PathVariable final UUID id) {
        return ResponseEntity.ok(orderProjectionService.findById(id));
    }
}
