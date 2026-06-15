package com.example.orderservice.payment;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.payment.strategy.BankTransferPaymentStrategy;
import com.example.orderservice.payment.strategy.CreditCardPaymentStrategy;
import com.example.orderservice.payment.strategy.CryptoPaymentStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStrategyRegistryTest {

    @Test
    void resolve_returnsStrategyMatchingTheMethod() {
        PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(
                new CreditCardPaymentStrategy(),
                new BankTransferPaymentStrategy(),
                new CryptoPaymentStrategy()));

        assertThat(registry.resolve(PaymentMethod.CREDIT_CARD)).isInstanceOf(CreditCardPaymentStrategy.class);
        assertThat(registry.resolve(PaymentMethod.BANK_TRANSFER)).isInstanceOf(BankTransferPaymentStrategy.class);
        assertThat(registry.resolve(PaymentMethod.CRYPTO)).isInstanceOf(CryptoPaymentStrategy.class);
    }

    @Test
    void resolve_unregisteredMethod_throws() {
        PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(new CreditCardPaymentStrategy()));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registry.resolve(PaymentMethod.CRYPTO));
    }

    @Test
    void construction_withDuplicateMethod_failsFast() {
        PaymentStrategy first = Mockito.mock(PaymentStrategy.class);
        PaymentStrategy second = Mockito.mock(PaymentStrategy.class);
        Mockito.when(first.getPaymentMethod()).thenReturn(PaymentMethod.CREDIT_CARD);
        Mockito.when(second.getPaymentMethod()).thenReturn(PaymentMethod.CREDIT_CARD);

        assertThatThrownBy(() -> new PaymentStrategyRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }
}
