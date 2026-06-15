package com.example.orderservice.api.dto.response;

public record BulkOrderItemResponse(
        int index,
        String status,
        Long orderId,
        String error
) {
}
