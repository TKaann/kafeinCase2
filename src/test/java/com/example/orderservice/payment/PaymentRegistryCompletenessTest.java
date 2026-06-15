package com.example.orderservice.payment;

import com.example.orderservice.domain.enums.PaymentMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Open/Closed safety net: every {@link PaymentMethod} must resolve to a registered strategy in
 * the fully wired application context. If someone adds an enum value without a corresponding
 * {@code @Component} strategy, this test fails immediately.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentRegistryCompletenessTest {

    @Autowired
    private PaymentStrategyRegistry registry;

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    void everyPaymentMethodResolvesToAStrategy(PaymentMethod method) {
        assertThat(registry.resolve(method)).isNotNull();
    }
}
