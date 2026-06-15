package com.example.orderservice.payment.strategy;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.payment.PaymentResult;
import com.example.orderservice.payment.PaymentStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class CryptoPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CRYPTO;
    }

    @Override
    public PaymentResult pay(BigDecimal amount) {
        String ref = "CRYPTO-" + UUID.randomUUID();
        log.info("Processing CRYPTO payment of {} -> ref {}", amount, ref);
        return PaymentResult.success(ref);
    }
}
