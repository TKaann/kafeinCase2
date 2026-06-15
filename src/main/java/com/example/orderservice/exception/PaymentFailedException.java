package com.example.orderservice.exception;

import com.example.orderservice.domain.enums.PaymentMethod;

/**
 * Thrown when a payment attempt fails. Being unchecked, it triggers the surrounding
 * order transaction to roll back, so a failed payment never leaves a half-committed order.
 */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(PaymentMethod method, String reason) {
        super("Payment failed via " + method + ": " + reason);
    }
}
