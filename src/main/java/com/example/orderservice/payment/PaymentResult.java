package com.example.orderservice.payment;

/**
 * Outcome of a payment attempt. Immutable value object returned by a {@link PaymentStrategy}.
 */
public record PaymentResult(boolean success, String transactionReference, String message) {

    public static PaymentResult success(String transactionReference) {
        return new PaymentResult(true, transactionReference, "Payment approved");
    }

    public static PaymentResult failure(String message) {
        return new PaymentResult(false, null, message);
    }
}
