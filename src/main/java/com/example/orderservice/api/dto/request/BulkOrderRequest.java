package com.example.orderservice.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkOrderRequest(

        @NotEmpty(message = "a bulk request must contain at least one order")
        @Valid
        List<CreateOrderRequest> orders
) {
}
