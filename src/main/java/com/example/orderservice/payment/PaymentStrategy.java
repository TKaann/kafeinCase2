package com.example.orderservice.payment;

import com.example.orderservice.domain.enums.PaymentMethod;
import java.math.BigDecimal;

/**
 * Strategy abstraction for processing a payment. Each supported {@link PaymentMethod}
 * has its own implementation. Adding a new method means adding a new @Component that
 * implements this interface — no existing code changes (Open/Closed Principle).
 */
public interface PaymentStrategy {

    /** The payment method this strategy handles. Used by the registry for auto-wiring. */
    PaymentMethod getPaymentMethod();

    /** Attempt to charge {@code amount}. Implementations must not throw for a normal decline. */
    PaymentResult pay(BigDecimal amount);
}
