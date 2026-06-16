package com.example.orderservice.payment.strategy;

import com.example.orderservice.config.PaymentProperties;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.payment.PaymentResult;
import com.example.orderservice.payment.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditCardPaymentStrategy implements PaymentStrategy {

    private final PaymentProperties paymentProperties;

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentResult pay(BigDecimal amount) {
        BigDecimal limit = paymentProperties.creditCardLimit();
        if (amount.compareTo(limit) > 0) {
            log.info("CREDIT_CARD payment of {} declined: exceeds limit {}", amount, limit);
            return PaymentResult.failure("Credit card limit exceeded: limit=" + limit + ", amount=" + amount);
        }
        String ref = "CC-" + UUID.randomUUID();
        log.info("Processing CREDIT_CARD payment of {} -> ref {}", amount, ref);
        return PaymentResult.success(ref);
    }
}
