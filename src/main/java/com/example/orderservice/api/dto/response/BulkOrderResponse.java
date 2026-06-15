package com.example.orderservice.api.dto.response;

import java.util.List;

/**
 * Result of a bulk order request. The HTTP call itself succeeds (200); individual order
 * outcomes are carried per-item, so partial failures are data rather than a transport error.
 */
public record BulkOrderResponse(
        int total,
        int succeeded,
        int failed,
        List<BulkOrderItemResponse> results
) {
}
