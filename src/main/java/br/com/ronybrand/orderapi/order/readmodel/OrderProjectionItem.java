package br.com.ronybrand.orderapi.order.readmodel;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderProjectionItem(UUID id, String description, BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {
}
