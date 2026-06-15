package com.example.orderservice.payment;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.payment.strategy.BankTransferPaymentStrategy;
import com.example.orderservice.payment.strategy.CreditCardPaymentStrategy;
import com.example.orderservice.payment.strategy.CryptoPaymentStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStrategyTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Test
    void creditCard_declaresMethodAndApprovesWithPrefixedReference() {
        PaymentStrategy strategy = new CreditCardPaymentStrategy();

        assertThat(strategy.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);

        PaymentResult result = strategy.pay(AMOUNT);
        assertThat(result.success()).isTrue();
        assertThat(result.transactionReference()).startsWith("CC-");
    }

    @Test
    void bankTransfer_declaresMethodAndApprovesWithPrefixedReference() {
        PaymentStrategy strategy = new BankTransferPaymentStrategy();

        assertThat(strategy.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);

        PaymentResult result = strategy.pay(AMOUNT);
        assertThat(result.success()).isTrue();
        assertThat(result.transactionReference()).startsWith("BT-");
    }

    @Test
    void crypto_declaresMethodAndApprovesWithPrefixedReference() {
        PaymentStrategy strategy = new CryptoPaymentStrategy();

        assertThat(strategy.getPaymentMethod()).isEqualTo(PaymentMethod.CRYPTO);

        PaymentResult result = strategy.pay(AMOUNT);
        assertThat(result.success()).isTrue();
        assertThat(result.transactionReference()).startsWith("CRYPTO-");
    }
}
