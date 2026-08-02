package br.com.ronybrand.orderapi.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(String fromAddress) {
}
