package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Denormalized read-model of {@code Order}, fed asynchronously (eventual consistency) by
 * {@link OrderProjectionService}. {@code id} is the write-side Order's UUID (as String) - same
 * identity, different storage engine. No {@code @Version}: every field except {@code deletedAt} is
 * set atomically on every upsert, and last-write-wins between two concurrent upserts is an
 * accepted trade-off for a disposable projection (unlike the Order aggregate root, which genuinely
 * needs optimistic locking against concurrent HTTP writers). What is not accepted as ordinary
 * staleness is an upsert resurrecting an already-deleted view - see {@code deletedAt} below.
 *
 * <p>{@code deletedAt} makes a delete a tombstone (a save, not a Mongo-level remove) rather than
 * an outright removal: the upsert and delete queues run on two independent consumer threads with
 * no ordering guarantee between them, so a delayed/racing upsert can be mid-flight when the delete
 * lands. Once tombstoned, an order can never be mutated again ({@code @SQLRestriction} on the
 * write-side {@code Order} blocks it), so any upsert that would apply to a tombstoned id is
 * necessarily stale. {@link OrderProjectionService#upsert} enforces this atomically (a single
 * conditional Mongo {@code upsert} command, not a separate read-then-write), so no interleaving
 * between the two threads can resurrect a tombstoned view.
 */
@Getter
@Builder
@Document(collection = "order_views")
public class OrderView {

    @Id
    private String id;

    private UUID customerId;
    private OrderStatus status;
    private List<OrderViewItem> items;
    private BigDecimal totalAmount;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
