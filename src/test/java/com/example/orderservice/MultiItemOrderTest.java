package com.example.orderservice;

import com.example.orderservice.domain.enums.OrderStatus;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.enums.PaymentStatus;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.exception.InsufficientStockException;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.PaymentRepository;
import com.example.orderservice.repository.ProductRepository;
import com.example.orderservice.service.CreateOrderCommand;
import com.example.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Atomicity of an order spanning two different products. Complements
 * {@link OrderServiceRollbackTest} (payment-triggered rollback) by exercising a
 * <em>stock-triggered</em> rollback across multiple line items.
 */
@SpringBootTest
@ActiveProfiles("test")
class MultiItemOrderTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void multiItemOrder_happyPath_decrementsBothAndConfirms() {
        Product a = save("Product A", "100.00", 10);
        Product b = save("Product B", "50.00", 5);
        long ordersBefore = orderRepository.count();

        CreateOrderCommand command = new CreateOrderCommand(
                1L, PaymentMethod.CREDIT_CARD,
                List.of(new CreateOrderCommand.Line(a.getId(), 2),
                        new CreateOrderCommand.Line(b.getId(), 3)));

        Order order = orderService.createOrder(command);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPayment()).isNotNull();
        assertThat(order.getPayment().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        // total = 2*100.00 + 3*50.00 = 350.00
        assertThat(order.getTotalAmount()).isEqualByComparingTo("350.00");

        assertThat(productRepository.findById(a.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(productRepository.findById(b.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
    }

    @Test
    void multiItemOrder_secondItemOutOfStock_rollsBackEverything() {
        Product a = save("Product A", "100.00", 10);   // lower id -> locked & processed first
        Product b = save("Product B", "50.00", 1);     // only 1 in stock
        long ordersBefore = orderRepository.count();
        long paymentsBefore = paymentRepository.count();

        CreateOrderCommand command = new CreateOrderCommand(
                1L, PaymentMethod.CREDIT_CARD,
                List.of(new CreateOrderCommand.Line(a.getId(), 2),
                        new CreateOrderCommand.Line(b.getId(), 5)));   // 5 > 1 -> fails on B

        assertThatExceptionOfType(InsufficientStockException.class)
                .isThrownBy(() -> orderService.createOrder(command));

        // A's stock was decremented in-memory before B failed, but the whole transaction rolled back.
        assertThat(productRepository.findById(a.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productRepository.findById(b.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
    }

    private Product save(String name, String price, int stock) {
        return productRepository.save(Product.builder()
                .name(name).price(new BigDecimal(price)).stockQuantity(stock).build());
    }
}
