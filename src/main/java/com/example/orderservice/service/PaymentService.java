package com.example.orderservice.service;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.enums.PaymentStatus;
import com.example.orderservice.domain.model.Payment;
import com.example.orderservice.exception.PaymentFailedException;
import com.example.orderservice.payment.PaymentResult;
import com.example.orderservice.payment.PaymentStrategy;
import com.example.orderservice.payment.PaymentStrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Dispatches a payment to the correct {@link PaymentStrategy} via the registry, then maps the
 * result into a {@link Payment} entity. A declined or failed payment raises
 * {@link PaymentFailedException} so the surrounding order transaction rolls back atomically.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentStrategyRegistry registry;

    public Payment charge(PaymentMethod method, BigDecimal amount) {
        PaymentStrategy strategy = registry.resolve(method);
        PaymentResult result = strategy.pay(amount);

        if (!result.success()) {
            throw new PaymentFailedException(method, result.message());
        }

        return Payment.builder()
                .paymentMethod(method)
                .amount(amount)
                .status(PaymentStatus.COMPLETED)
                .transactionReference(result.transactionReference())
                .build();
    }
}
