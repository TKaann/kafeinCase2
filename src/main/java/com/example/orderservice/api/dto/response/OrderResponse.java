package com.example.orderservice.api.dto.response;

import com.example.orderservice.domain.enums.OrderStatus;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String transactionReference,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
}
