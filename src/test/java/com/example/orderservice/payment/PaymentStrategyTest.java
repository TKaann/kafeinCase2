package com.example.orderservice.payment;

import com.example.orderservice.config.PaymentProperties;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.payment.strategy.BankTransferPaymentStrategy;
import com.example.orderservice.payment.strategy.CreditCardPaymentStrategy;
import com.example.orderservice.payment.strategy.CryptoPaymentStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStrategyTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final PaymentProperties LIMIT_10K = new PaymentProperties(new BigDecimal("10000"));

    @Test
    void creditCard_declaresMethodAndApprovesWithinLimit() {
        PaymentStrategy strategy = new CreditCardPaymentStrategy(LIMIT_10K);

        assertThat(strategy.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);

        PaymentResult result = strategy.pay(AMOUNT);
        assertThat(result.success()).isTrue();
        assertThat(result.transactionReference()).startsWith("CC-");
    }

    @Test
    void creditCard_atLimitApproves_overLimitDeclines() {
        PaymentStrategy strategy = new CreditCardPaymentStrategy(LIMIT_10K);

        assertThat(strategy.pay(new BigDecimal("10000")).success()).isTrue();

        PaymentResult overLimit = strategy.pay(new BigDecimal("10000.01"));
        assertThat(overLimit.success()).isFalse();
        assertThat(overLimit.message()).containsIgnoringCase("limit");
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
