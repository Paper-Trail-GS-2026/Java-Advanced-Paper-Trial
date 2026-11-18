package br.com.fiap.PaperTrail.integration.catalogevents;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "papertrail.catalog-events")
public record CatalogEventsProperties(
        boolean enabled,
        String exchange,
        String routingKey,
        String queue,
        Duration confirmTimeout,
        int maxAttempts,
        Duration retryDelay,
        Duration reconcileInterval) {

    public CatalogEventsProperties {
        exchange = blank(exchange) ? "papertrail.catalog" : exchange;
        routingKey = blank(routingKey) ? "catalog.service.updated" : routingKey;
        queue = blank(queue) ? "papertrail.dotnet.catalog-version" : queue;
        confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(5) : confirmTimeout;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryDelay = retryDelay == null ? Duration.ofMillis(500) : retryDelay;
        reconcileInterval = reconcileInterval == null ? Duration.ofMinutes(5) : reconcileInterval;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
