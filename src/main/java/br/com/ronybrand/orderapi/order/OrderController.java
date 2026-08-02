package br.com.ronybrand.orderapi.order;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController implements SearchControllerSupport<OrderResponseDto> {

    private final OrderService orderService;

    /**
     * Same filter syntax as {@code /customers/search} - see
     * {@link br.com.ronybrand.orderapi.customer.CustomerController#search}.
     */
    @GetMapping("/search")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Search orders", operationId = "searchOrders")
    public ResponseEntity<Page<OrderResponseDto>> search(@RequestParam final MultiValueMap<String, String> allParams,
            @RequestParam(required = false) final String order,
            @RequestParam(required = false, defaultValue = "0") final int page,
            @RequestParam(required = false, defaultValue = "20") final int size) {
        return doSearchByGetMethod(allParams, order, page, size, orderService);
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.InvalidInputException if customerId does not exist or the item list exceeds 200 items
     */
    @PostMapping
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Create a new order", operationId = "createOrder")
    public ResponseEntity<OrderResponseDto> create(@RequestBody @Valid final OrderCreateRequestDto request) {
        final OrderResponseDto created = orderService.create(request.customerId(), request.items());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the order does not exist
     */
    @GetMapping("/{id}")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Find an order by id", operationId = "findOrderById")
    public ResponseEntity<OrderResponseDto> findById(@PathVariable final UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the order does not exist
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Soft-delete an order", operationId = "deleteOrder")
    public ResponseEntity<Void> delete(@PathVariable final UUID id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the order does not exist
     * @throws br.com.ronybrand.orderapi.commons.exception.InvalidInputException if the order is not OPEN
     */
    @PostMapping("/{id}/items")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Add an item to an order", operationId = "addOrderItem")
    public ResponseEntity<OrderResponseDto> addItem(@PathVariable final UUID id, @RequestBody @Valid final ItemRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addItem(id, request));
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the order or item does not exist
     * @throws br.com.ronybrand.orderapi.commons.exception.InvalidInputException if the order is not OPEN
     */
    @PatchMapping("/{orderId}/items/{itemId}")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Update an item's quantity", operationId = "updateOrderItemQuantity")
    public ResponseEntity<OrderResponseDto> updateItemQuantity(@PathVariable final UUID orderId, @PathVariable final UUID itemId,
            @RequestBody @Valid final ItemQuantityUpdateRequestDto request) {
        return ResponseEntity.ok(orderService.updateItemQuantity(orderId, itemId, request.quantity()));
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the order or item does not exist
     * @throws br.com.ronybrand.orderapi.commons.exception.InvalidInputException if the order is not OPEN
     */
    @DeleteMapping("/{orderId}/items/{itemId}")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Remove an item from an order", operationId = "removeOrderItem")
    public ResponseEntity<OrderResponseDto> removeItem(@PathVariable final UUID orderId, @PathVariable final UUID itemId) {
        return ResponseEntity.ok(orderService.removeItem(orderId, itemId));
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the order does not exist
     * @throws br.com.ronybrand.orderapi.commons.exception.InvalidInputException if the order is not OPEN, or has no items
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Confirm an order", operationId = "confirmOrder")
    public ResponseEntity<OrderResponseDto> confirm(@PathVariable final UUID id) {
        return ResponseEntity.ok(orderService.confirm(id));
    }

    /**
     * @throws br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException if the order does not exist
     * @throws br.com.ronybrand.orderapi.commons.exception.InvalidInputException if the order is already CANCELED
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(Constants.HAS_ROLE_USER)
    @Operation(summary = "Cancel an order", operationId = "cancelOrder")
    public ResponseEntity<OrderResponseDto> cancel(@PathVariable final UUID id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }
}
