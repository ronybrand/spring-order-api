package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class OrderProjectionServiceTest {

    private final OrderViewRepository orderViewRepository = mock(OrderViewRepository.class);
    private final OrderProjectionService service = new OrderProjectionService(orderViewRepository);

    private static OrderProjectionMessage message(final UUID orderId) {
        final OrderProjectionItem item = new OrderProjectionItem(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        return new OrderProjectionMessage(orderId, UUID.randomUUID(), OrderStatus.CONFIRMED, List.of(item),
                new BigDecimal("20.00"), LocalDateTime.now());
    }

    @Test
    void upsert_ShouldSaveMappedOrderView_WhenSucceeds() {
        final UUID orderId = UUID.randomUUID();
        final OrderProjectionMessage message = message(orderId);

        service.upsert(message);

        final ArgumentCaptor<OrderView> captor = ArgumentCaptor.forClass(OrderView.class);
        verify(orderViewRepository).save(captor.capture());
        final OrderView saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(orderId.toString());
        assertThat(saved.getCustomerId()).isEqualTo(message.customerId());
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getDescription()).isEqualTo("Widget");
    }

    @Test
    void upsert_ShouldThrowOrderProjectionWriteException_WhenRepositoryFails() {
        final OrderProjectionMessage message = message(UUID.randomUUID());
        when(orderViewRepository.save(any(OrderView.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> service.upsert(message)).isInstanceOf(OrderProjectionWriteException.class);
    }

    @Test
    void findById_ShouldReturnOrderViewResponseDto_WhenExists() {
        final UUID orderId = UUID.randomUUID();
        final OrderView view = OrderView.builder()
                .id(orderId.toString())
                .customerId(UUID.randomUUID())
                .status(OrderStatus.OPEN)
                .items(List.of())
                .totalAmount(BigDecimal.ZERO)
                .updatedAt(LocalDateTime.now())
                .build();
        when(orderViewRepository.findById(orderId.toString())).thenReturn(Optional.of(view));

        final OrderViewResponseDto result = service.findById(orderId);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void findById_ShouldThrowResourceNotFoundException_WhenNotExists() {
        final UUID orderId = UUID.randomUUID();
        when(orderViewRepository.findById(orderId.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
