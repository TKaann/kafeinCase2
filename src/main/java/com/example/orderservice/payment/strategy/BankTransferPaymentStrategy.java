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
public class BankTransferPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public PaymentResult pay(BigDecimal amount) {
        String ref = "BT-" + UUID.randomUUID();
        log.info("Processing BANK_TRANSFER payment of {} -> ref {}", amount, ref);
        return PaymentResult.success(ref);
    }
}
