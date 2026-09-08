package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProjectionService {

    private final OrderViewRepository orderViewRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Atomically upserts through {@link MongoTemplate} instead of the earlier find-then-save: the
     * upsert queue ({@link OrderProjectionRabbitListener}) and the delete queue
     * ({@link OrderDeletionRabbitListener}) run on two independent consumer threads with no
     * ordering guarantee between them, so a separate {@code findById} check followed by a separate
     * {@code save} left a window in which {@link #deleteById}'s tombstone write could land in
     * between - silently resurrecting a view the write side considers permanently gone. A single
     * {@code upsert(query, update)} command is one atomic operation on the Mongo server: the query
     * requires {@code deletedAt} to be absent, so if the document has already been tombstoned the
     * query matches nothing and Mongo attempts to *insert* a new document sharing the same
     * {@code _id} - which fails with {@link DuplicateKeyException}, the exact (and only) signal
     * needed to detect and skip a stale upsert racing a delete, with no read-then-write gap.
     */
    public void upsert(final OrderProjectionMessage message) {
        final String id = message.orderId().toString();
        try {
            mongoTemplate.upsert(notTombstonedQuery(id), toUpdate(message), OrderView.class);
        } catch (final DuplicateKeyException e) {
            log.info("Order already deleted, skipping stale projection upsert: orderId={}", message.orderId());
            return;
        } catch (final DataAccessException e) {
            throw new OrderProjectionWriteException("Failed to upsert order view: orderId=" + message.orderId(), e);
        }
        log.info("Order view upserted: orderId={}", message.orderId());
    }

    /**
     * Replaces the view with a tombstone (a save, not a Mongo remove) so {@link #upsert} can detect
     * and skip a delayed upsert that arrives afterwards, instead of resurrecting it.
     */
    public void deleteById(final UUID orderId) {
        try {
            orderViewRepository.save(OrderView.builder().id(orderId.toString())
                    .deletedAt(LocalDateTime.now(ZoneOffset.UTC)).build());
        } catch (final DataAccessException e) {
            throw new OrderProjectionWriteException("Failed to delete order view: orderId=" + orderId, e);
        }
        log.info("Order view deleted: orderId={}", orderId);
    }

    public OrderViewResponseDto findById(final UUID orderId) {
        return orderViewRepository.findById(orderId.toString())
                .filter(view -> view.getDeletedAt() == null)
                .map(OrderViewResponseDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order view not found", ErrorCode.RESOURCE_NOT_FOUND_ORDER_VIEW));
    }

    private static Query notTombstonedQuery(final String id) {
        return Query.query(Criteria.where("_id").is(id).and("deletedAt").exists(false));
    }

    private static Update toUpdate(final OrderProjectionMessage message) {
        final List<OrderViewItem> items = message.items().stream().map(OrderProjectionService::toViewItem).toList();
        return new Update()
                .set("customerId", message.customerId())
                .set("status", message.status())
                .set("items", items)
                .set("totalAmount", message.totalAmount())
                .set("updatedAt", message.updatedAt());
    }

    private static OrderViewItem toViewItem(final OrderProjectionItem item) {
        return OrderViewItem.builder()
                .id(item.id())
                .description(item.description())
                .unitPrice(item.unitPrice())
                .quantity(item.quantity())
                .subtotal(item.subtotal())
                .build();
    }
}
