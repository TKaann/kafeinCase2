package com.example.orderservice.service;

import com.example.orderservice.domain.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends customer notifications. The case only requires logging instead of real mail/SMS,
 * so this is a deliberately simple, synchronous log. Kept behind its own service so the
 * delivery mechanism (event-driven, async, real provider) can change without touching
 * order logic.
 */
@Slf4j
@Service
public class NotificationService {

    public void notifyOrderConfirmed(Order order) {
        log.info("[NOTIFICATION] Order {} confirmed for customer {} — total {}, paid via {}",
                order.getId(), order.getCustomerId(), order.getTotalAmount(), order.getPaymentMethod());
    }
}
