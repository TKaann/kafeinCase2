package com.example.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalized CORS configuration. Origins are configurable via {@code app.cors.allowed-origins}
 * (a list) so a future test-harness frontend can be pointed at the API without code changes.
 * Falls back to common local dev ports when unset.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:5173", "http://localhost:3000");
        }
    }
}
