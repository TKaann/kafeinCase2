package com.example.orderservice.api.exception;

import java.time.LocalDateTime;

/**
 * Standard error body returned by {@link GlobalExceptionHandler}.
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
