package com.example.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Payment configuration. {@code app.payment.credit-card-limit} caps a single credit-card payment;
 * orders above it are declined (which drives the existing 422 + rollback flow). Bank transfer and
 * crypto are intentionally limitless. Defaults to 10000 when unset.
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(BigDecimal creditCardLimit) {

    public PaymentProperties {
        if (creditCardLimit == null) {
            creditCardLimit = new BigDecimal("10000");
        }
    }
}
