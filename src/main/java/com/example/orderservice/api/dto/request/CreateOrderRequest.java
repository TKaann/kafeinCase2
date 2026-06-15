package com.example.orderservice.api.dto.request;

import com.example.orderservice.domain.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(

        @NotNull(message = "customerId is required")
        Long customerId,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "an order must contain at least one item")
        @Valid
        List<OrderItemRequest> items
) {
}
