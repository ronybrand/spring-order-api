package br.com.ronybrand.orderapi.order.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class OrderProjectionServiceTest {

    private final OrderViewRepository orderViewRepository = mock(OrderViewRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final OrderProjectionService service = new OrderProjectionService(orderViewRepository, mongoTemplate);

    private static OrderProjectionMessage message(final UUID orderId) {
        final OrderProjectionItem item = new OrderProjectionItem(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        return new OrderProjectionMessage(orderId, UUID.randomUUID(), OrderStatus.CONFIRMED, List.of(item),
                new BigDecimal("20.00"), LocalDateTime.now());
    }

    private static OrderView tombstone(final UUID orderId) {
        return OrderView.builder().id(orderId.toString()).deletedAt(LocalDateTime.now()).build();
    }

    @Test
    void upsert_ShouldAtomicallyUpsertViaMongoTemplate_ConditionedOnNoTombstone() {
        final UUID orderId = UUID.randomUUID();
        final OrderProjectionMessage message = message(orderId);

        service.upsert(message);

        final ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        final ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(OrderView.class));

        final Document queryDoc = queryCaptor.getValue().getQueryObject();
        assertThat(queryDoc.get("_id")).isEqualTo(orderId.toString());
        assertThat(((Document) queryDoc.get("deletedAt")).get("$exists")).isEqualTo(false);

        final Document setDoc = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(setDoc.get("customerId")).isEqualTo(message.customerId());
        assertThat(setDoc.get("status")).isEqualTo(OrderStatus.CONFIRMED);
        assertThat((BigDecimal) setDoc.get("totalAmount")).isEqualByComparingTo("20.00");
        assertThat(setDoc.containsKey("deletedAt")).isFalse();
    }

    @Test
    void upsert_ShouldSkipSilently_WhenMongoRejectsAsDuplicateKey_MeaningATombstoneAlreadyExists() {
        final UUID orderId = UUID.randomUUID();
        final OrderProjectionMessage message = message(orderId);
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(OrderView.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        service.upsert(message);
    }

    @Test
    void upsert_ShouldThrowOrderProjectionWriteException_WhenMongoTemplateFails() {
        final OrderProjectionMessage message = message(UUID.randomUUID());
        doThrow(new DataAccessResourceFailureException("down"))
                .when(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(OrderView.class));

        assertThatThrownBy(() -> service.upsert(message)).isInstanceOf(OrderProjectionWriteException.class);
    }

    @Test
    void deleteById_ShouldSaveTombstone_WhenSucceeds() {
        final UUID orderId = UUID.randomUUID();

        service.deleteById(orderId);

        final ArgumentCaptor<OrderView> captor = ArgumentCaptor.forClass(OrderView.class);
        verify(orderViewRepository).save(captor.capture());
        final OrderView saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(orderId.toString());
        assertThat(saved.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteById_ShouldThrowOrderProjectionWriteException_WhenRepositoryFails() {
        final UUID orderId = UUID.randomUUID();
        when(orderViewRepository.save(any(OrderView.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> service.deleteById(orderId)).isInstanceOf(OrderProjectionWriteException.class);
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

    @Test
    void findById_ShouldThrowResourceNotFoundException_WhenTombstoned() {
        final UUID orderId = UUID.randomUUID();
        when(orderViewRepository.findById(orderId.toString())).thenReturn(Optional.of(tombstone(orderId)));

        assertThatThrownBy(() -> service.findById(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
