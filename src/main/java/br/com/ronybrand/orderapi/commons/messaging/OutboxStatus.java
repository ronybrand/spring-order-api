package br.com.ronybrand.orderapi.commons.messaging;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}