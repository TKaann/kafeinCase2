package com.example.orderservice;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.exception.PaymentFailedException;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.ProductRepository;
import com.example.orderservice.service.CreateOrderCommand;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves the transaction is atomic: when payment fails, the stock decrement and the order
 * insert that happened earlier in the same transaction are rolled back. Payment is replaced
 * with a mock that throws, so the failure is deterministic and isolated to this concern.
 *
 * <p>This also confirms that proxy-based {@code @Transactional} still works after dropping the
 * {@code spring-boot-starter-aop} dependency.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceRollbackTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private PaymentService paymentService;

    @Test
    void paymentFailure_rollsBackStockAndOrder() {
        Product product = productRepository.save(Product.builder()
                .name("Rollback Test Item")
                .price(new BigDecimal("25.00"))
                .stockQuantity(10)
                .build());
        long ordersBefore = orderRepository.count();

        when(paymentService.charge(any(), any()))
                .thenThrow(new PaymentFailedException(PaymentMethod.CREDIT_CARD, "gateway unavailable"));

        CreateOrderCommand command = new CreateOrderCommand(
                1L, PaymentMethod.CREDIT_CARD,
                List.of(new CreateOrderCommand.Line(product.getId(), 3)));

        assertThatExceptionOfType(PaymentFailedException.class)
                .isThrownBy(() -> orderService.createOrder(command));

        // Stock decrement was rolled back...
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        // ...and no order was persisted.
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }
}
