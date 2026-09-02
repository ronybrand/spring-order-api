package br.com.ronybrand.orderapi.order.readmodel;

import br.com.ronybrand.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Denormalized read-model of {@code Order}, fed asynchronously (eventual consistency) by
 * {@link OrderProjectionService}. {@code id} is the write-side Order's UUID (as String) - same
 * identity, different storage engine. No {@code @Version}: this document is fully replaced on
 * every upsert, and last-write-wins is an accepted trade-off for a disposable projection (unlike
 * the Order aggregate root, which genuinely needs optimistic locking against concurrent HTTP
 * writers).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "order_views")
public class OrderView {

    @Id
    private String id;

    private UUID customerId;
    private OrderStatus status;
    private List<OrderViewItem> items;
    private BigDecimal totalAmount;
    private LocalDateTime updatedAt;
}
