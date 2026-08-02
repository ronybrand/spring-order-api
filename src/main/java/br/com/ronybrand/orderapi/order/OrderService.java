package br.com.ronybrand.orderapi.order;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.customer.CustomerRepository;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private static final String SYSTEM_USER = "system";

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final AuditorAware<String> auditorAware;

    /**
     * customerId that doesn't resolve to an existing customer is a 400
     * ({@link ErrorCode#VALIDATION_INVALID_CUSTOMER_ID}), not a 404 - DOMAIN.md §6 reserves
     * {@code RESOURCE_NOT_FOUND_CUSTOMER} for direct customer lookups, not for this validation of
     * the order-creation request.
     */
    @Transactional
    OrderResponseDto create(@NotNull final UUID customerId, @NotNull final List<ItemRequestDto> itemRequests) {
        final Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new InvalidInputException("customerId does not exist", ErrorCode.VALIDATION_INVALID_CUSTOMER_ID));

        final Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.OPEN)
                .build();
        final List<Item> items = itemRequests.stream()
                .map(itemRequest -> Item.builder()
                        .order(order)
                        .description(itemRequest.description())
                        .unitPrice(itemRequest.unitPrice())
                        .quantity(itemRequest.quantity())
                        .build())
                .toList();
        order.getItems().addAll(items);
        order.calculateTotal();

        final Order saved = orderRepository.save(order);
        log.info("Order created: id={}", saved.getId());
        return OrderResponseDto.from(saved);
    }

    @Transactional(readOnly = true)
    OrderResponseDto findById(@NotNull final UUID id) {
        return OrderResponseDto.from(findByIdOrThrow(id));
    }

    @Transactional
    void delete(@NotNull final UUID id) {
        final Order order = findByIdOrThrow(id);
        order.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        order.setDeletedBy(auditorAware.getCurrentAuditor().orElse(SYSTEM_USER));
        orderRepository.save(order);
        log.info("Order deleted: id={}", id);
    }

    private Order findByIdOrThrow(final UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found", ErrorCode.RESOURCE_NOT_FOUND_ORDER));
    }
}
