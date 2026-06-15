package com.example.orderservice.service;

import com.example.orderservice.domain.enums.PaymentMethod;

import java.util.List;

/**
 * Service-layer input for creating an order. Decoupled from the web DTO so the order use case
 * does not depend on the API representation.
 */
public record CreateOrderCommand(Long customerId, PaymentMethod paymentMethod, List<Line> lines) {

    public record Line(Long productId, int quantity) {
    }
}
