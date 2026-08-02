package br.com.ronybrand.orderapi.commons.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pagination")
public record PaginationProperties(int defaultPage, int defaultSize, int maxSize) {
}
