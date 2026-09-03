package br.com.ronybrand.orderapi.order;

import br.com.ronybrand.orderapi.commons.config.PaginationProperties;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import br.com.ronybrand.orderapi.commons.exception.RepositoryLookups;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.commons.filter.SearchService;
import br.com.ronybrand.orderapi.commons.filter.SearchUtils;
import br.com.ronybrand.orderapi.customer.Customer;
import br.com.ronybrand.orderapi.customer.CustomerRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class OrderService implements SearchService<OrderResponseDto> {

    private static final String SYSTEM_USER = "system";

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final AuditorAware<String> auditorAware;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final PaginationProperties paginationProperties;

    /**
     * customerId that doesn't resolve to an existing customer is a 400
     * ({@link ErrorCode#VALIDATION_INVALID_CUSTOMER_ID}), not a 404 - DOMAIN.md §6 reserves
     * {@code RESOURCE_NOT_FOUND_CUSTOMER} for direct customer lookups, not for this validation of
     * the order-creation request.
     *
     * <p>Reads the customer with {@code FOR SHARE} ({@link CustomerRepository#findByIdForShare})
     * rather than a plain read, so this blocks behind (and then correctly re-reads after) a
     * concurrent {@code CustomerService.delete} for the same customer instead of racing it -
     * DOMAIN.md §4.8.
     */
    @Transactional
    OrderResponseDto create(@NotNull final UUID customerId, @NotNull final List<ItemRequestDto> itemRequests) {
        final Customer customer = customerRepository.findByIdForShare(customerId)
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
        eventPublisher.publishEvent(new OrderChangedEvent(saved.getId()));
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

    @Transactional
    OrderResponseDto addItem(@NotNull final UUID orderId, @NotNull final ItemRequestDto itemRequest) {
        final Order order = findByIdOrThrow(orderId);
        ensureEditable(order);

        final Item item = Item.builder()
                .order(order)
                .description(itemRequest.description())
                .unitPrice(itemRequest.unitPrice())
                .quantity(itemRequest.quantity())
                .build();
        order.getItems().add(item);
        order.calculateTotal();

        final Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderChangedEvent(saved.getId()));
        log.info("Item added to order: orderId={}", orderId);
        return OrderResponseDto.from(saved);
    }

    @Transactional
    OrderResponseDto updateItemQuantity(@NotNull final UUID orderId, @NotNull final UUID itemId, @NotNull final Integer quantity) {
        final Order order = findByIdOrThrow(orderId);
        ensureEditable(order);

        final Item item = findItemOrThrow(order, itemId);
        item.setQuantity(quantity);
        order.calculateTotal();

        final Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderChangedEvent(saved.getId()));
        log.info("Item quantity updated: orderId={}, itemId={}", orderId, itemId);
        return OrderResponseDto.from(saved);
    }

    @Transactional
    OrderResponseDto removeItem(@NotNull final UUID orderId, @NotNull final UUID itemId) {
        final Order order = findByIdOrThrow(orderId);
        ensureEditable(order);

        final Item item = findItemOrThrow(order, itemId);
        order.getItems().remove(item);
        order.calculateTotal();

        final Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderChangedEvent(saved.getId()));
        log.info("Item removed from order: orderId={}, itemId={}", orderId, itemId);
        return OrderResponseDto.from(saved);
    }

    /**
     * @throws InvalidInputException if the order is not OPEN (invalid transition) or has no items
     */
    @Transactional
    OrderResponseDto confirm(@NotNull final UUID id) {
        final Order order = findByIdOrThrow(id);
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new InvalidInputException("Invalid status transition", ErrorCode.VALIDATION_ORDER_INVALID_STATUS_TRANSITION);
        }
        if (order.getItems().isEmpty()) {
            throw new InvalidInputException("Order has no items", ErrorCode.VALIDATION_ORDER_EMPTY);
        }

        final OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CONFIRMED);
        final Order saved = orderRepository.save(order);
        publishStatusChangedEvent(saved, previousStatus, OrderStatus.CONFIRMED);
        eventPublisher.publishEvent(new OrderChangedEvent(saved.getId()));
        log.info("Order confirmed: id={}", id);
        return OrderResponseDto.from(saved);
    }

    /**
     * Allowed from OPEN or CONFIRMED; only already-CANCELED is rejected as an invalid transition
     * (DOMAIN.md §4.4). Reuses the same event-publishing helper as {@link #confirm}.
     *
     * @throws InvalidInputException if the order is already CANCELED
     */
    @Transactional
    OrderResponseDto cancel(@NotNull final UUID id) {
        final Order order = findByIdOrThrow(id);
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new InvalidInputException("Invalid status transition", ErrorCode.VALIDATION_ORDER_INVALID_STATUS_TRANSITION);
        }

        final OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELED);
        final Order saved = orderRepository.save(order);
        publishStatusChangedEvent(saved, previousStatus, OrderStatus.CANCELED);
        eventPublisher.publishEvent(new OrderChangedEvent(saved.getId()));
        log.info("Order canceled: id={}", id);
        return OrderResponseDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> search(final Map<String, List<String>> filters, final String order, final int page, final int size) {
        final Sort sort = SearchUtils.buildSort(order, entityManager.getMetamodel(), Order.class);
        final Pageable pageable = SearchUtils.buildPageable(page, size, paginationProperties.maxSize(), sort);
        return orderRepository.findAll(OrderSpecification.byCriteria(filters), pageable).map(OrderResponseDto::from);
    }

    private void publishStatusChangedEvent(final Order order, final OrderStatus oldStatus, final OrderStatus newStatus) {
        final Customer customer = order.getCustomer();
        if (StringUtils.isBlank(customer.getEmail())) {
            return;
        }
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getId(), customer.getEmail(), customer.getName(),
                oldStatus, newStatus, order.getTotal(), LocalDateTime.now(ZoneOffset.UTC)));
    }

    private void ensureEditable(final Order order) {
        if (!order.isEditable()) {
            throw new InvalidInputException("Order is not editable in its current status", ErrorCode.VALIDATION_ORDER_NOT_EDITABLE);
        }
    }

    private Item findItemOrThrow(final Order order, final UUID itemId) {
        return order.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item not found", ErrorCode.RESOURCE_NOT_FOUND_ITEM));
    }

    private Order findByIdOrThrow(final UUID id) {
        return RepositoryLookups.getOrThrow(orderRepository, id, ErrorCode.RESOURCE_NOT_FOUND_ORDER, "Order not found");
    }
}
