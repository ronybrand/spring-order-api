package br.com.ronybrand.orderapi.order;

/**
 * OPEN -&gt; CONFIRMED -&gt; CANCELED, or OPEN -&gt; CANCELED directly. CANCELED is terminal
 * (DOMAIN.md §3).
 */
public enum OrderStatus {
    OPEN,
    CONFIRMED,
    CANCELED
}
