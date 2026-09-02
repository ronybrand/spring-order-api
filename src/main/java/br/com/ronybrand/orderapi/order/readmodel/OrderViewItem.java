package br.com.ronybrand.orderapi.order.readmodel;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embedded in {@link OrderView}, not its own Mongo document. {@code subtotal} is precomputed once,
 * at projection time ({@code OrderChangedEventListener}), not recomputed on every read - the point
 * of a read-model is to avoid redoing that work per request, unlike {@code ItemResponseDto} on the
 * write side (which computes it on the fly).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderViewItem {

    private UUID id;
    private String description;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
}
