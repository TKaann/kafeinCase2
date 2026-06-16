package com.example.orderservice;

import com.example.orderservice.domain.enums.OrderStatus;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.enums.PaymentStatus;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.exception.PaymentFailedException;
import com.example.orderservice.repository.OrderRepository;
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
 * Exercises the credit-card limit through the <em>real</em> strategy (no mocks): an order above
 * the configured limit (default 10000) is declined, which triggers the standard
 * payment-failure rollback. A bank-transfer order of the same amount succeeds, proving the limit
 * is credit-card-specific.
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditCardLimitTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void creditCardOverLimit_declinesAndRollsBack() {
        // price 6000 x qty 2 = 12000 > 10000 limit
        Product product = productRepository.save(Product.builder()
                .name("Expensive Item").price(new BigDecimal("6000.00")).stockQuantity(10).build());
        long ordersBefore = orderRepository.count();

        CreateOrderCommand command = new CreateOrderCommand(
                1L, PaymentMethod.CREDIT_CARD,
                List.of(new CreateOrderCommand.Line(product.getId(), 2)));

        assertThatExceptionOfType(PaymentFailedException.class)
                .isThrownBy(() -> orderService.createOrder(command));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void bankTransferSameAmount_succeeds_provingLimitIsCreditCardOnly() {
        Product product = productRepository.save(Product.builder()
                .name("Expensive Item 2").price(new BigDecimal("6000.00")).stockQuantity(10).build());

        CreateOrderCommand command = new CreateOrderCommand(
                1L, PaymentMethod.BANK_TRANSFER,
                List.of(new CreateOrderCommand.Line(product.getId(), 2)));   // 12000, no limit

        Order order = orderService.createOrder(command);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPayment().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("12000.00");
    }
}
