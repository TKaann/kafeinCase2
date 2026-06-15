package com.example.orderservice.payment;

import com.example.orderservice.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the correct {@link PaymentStrategy} at runtime based on {@link PaymentMethod}.
 *
 * <p>Spring injects <em>all</em> {@code PaymentStrategy} beans into the constructor, and we
 * index them by their declared method. Adding a new payment method requires only a new
 * strategy @Component — this registry needs no changes (Open/Closed Principle).
 */
@Component
public class PaymentStrategyRegistry {

    private final Map<PaymentMethod, PaymentStrategy> strategies = new EnumMap<>(PaymentMethod.class);

    public PaymentStrategyRegistry(List<PaymentStrategy> strategyBeans) {
        for (PaymentStrategy strategy : strategyBeans) {
            PaymentStrategy previous = strategies.put(strategy.getPaymentMethod(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate PaymentStrategy registered for method " + strategy.getPaymentMethod());
            }
        }
    }

    public PaymentStrategy resolve(PaymentMethod method) {
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new IllegalArgumentException("No payment strategy registered for method: " + method);
        }
        return strategy;
    }
}
