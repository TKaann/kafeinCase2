package com.example.orderservice.service;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.enums.PaymentStatus;
import com.example.orderservice.domain.model.Payment;
import com.example.orderservice.exception.PaymentFailedException;
import com.example.orderservice.payment.PaymentResult;
import com.example.orderservice.payment.PaymentStrategy;
import com.example.orderservice.payment.PaymentStrategyRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentStrategyRegistry registry;

    @Mock
    private PaymentStrategy strategy;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void charge_onApproval_buildsCompletedPayment() {
        BigDecimal amount = new BigDecimal("50.00");
        when(registry.resolve(PaymentMethod.CREDIT_CARD)).thenReturn(strategy);
        when(strategy.pay(any())).thenReturn(PaymentResult.success("CC-ref-123"));

        Payment payment = paymentService.charge(PaymentMethod.CREDIT_CARD, amount);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
        assertThat(payment.getTransactionReference()).isEqualTo("CC-ref-123");
    }

    @Test
    void charge_onDecline_throwsPaymentFailed() {
        when(registry.resolve(PaymentMethod.CRYPTO)).thenReturn(strategy);
        when(strategy.pay(any())).thenReturn(PaymentResult.failure("insufficient funds"));

        assertThatExceptionOfType(PaymentFailedException.class)
                .isThrownBy(() -> paymentService.charge(PaymentMethod.CRYPTO, new BigDecimal("10.00")));
    }
}
