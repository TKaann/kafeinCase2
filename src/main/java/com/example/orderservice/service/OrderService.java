package com.example.orderservice.service;

import com.example.orderservice.domain.enums.OrderStatus;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderItem;
import com.example.orderservice.domain.model.Payment;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the single-order use case. The whole flow (stock check + stock decrement +
 * order/items persistence + payment) runs in one transaction: if any step fails — most
 * importantly insufficient stock or a failed payment — nothing is committed. No half order,
 * no changed stock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        Order order = Order.builder()
                .customerId(command.customerId())
                .paymentMethod(command.paymentMethod())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        // Lock products in a stable order (ascending id) so concurrent multi-product orders
        // can never acquire row locks in opposite order and deadlock.
        List<CreateOrderCommand.Line> orderedLines = command.lines().stream()
                .sorted(Comparator.comparing(CreateOrderCommand.Line::productId))
                .toList();

        for (CreateOrderCommand.Line line : orderedLines) {
            Product product = productService.getForUpdate(line.productId());
            product.decreaseStock(line.quantity());            // enforces invariant, may throw
            order.addItem(OrderItem.of(product, line.quantity()));
        }

        order.recalculateTotal();

        Payment payment = paymentService.charge(order.getPaymentMethod(), order.getTotalAmount());
        order.attachPayment(payment);
        order.markConfirmed();

        Order saved = orderRepository.save(order);
        notificationService.notifyOrderConfirmed(saved);
        log.info("Order {} created successfully ({} item(s), total {})",
                saved.getId(), saved.getItems().size(), saved.getTotalAmount());
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrderDetail(Long orderId) {
        return orderRepository.findWithDetailsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
