package com.example.orderservice.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Sets a product's stock to an absolute value. Used by the test harness to reset stock between
 * runs (e.g. after a 500-order concurrency test drains it to zero).
 */
public record UpdateStockRequest(

        @NotNull(message = "stockQuantity is required")
        @PositiveOrZero(message = "stockQuantity cannot be negative")
        Integer stockQuantity
) {
}
