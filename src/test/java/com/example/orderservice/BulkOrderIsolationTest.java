package com.example.orderservice;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.repository.ProductRepository;
import com.example.orderservice.service.BulkOrderResult;
import com.example.orderservice.service.BulkOrderService;
import com.example.orderservice.service.CreateOrderCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies failure isolation in parallel processing: a failing order is reported in its own
 * result and does not roll back or corrupt the orders that succeed.
 */
@SpringBootTest
@ActiveProfiles("test")
class BulkOrderIsolationTest {

    @Autowired
    private BulkOrderService bulkOrderService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void oneFailedOrderDoesNotAffectOthers() {
        Product product = productRepository.save(Product.builder()
                .name("Isolation Test Item")
                .price(new BigDecimal("10.00"))
                .stockQuantity(5)
                .build());
        Long productId = product.getId();

        List<CreateOrderCommand> commands = List.of(
                order(productId, 3),   // index 0 — fits
                order(productId, 10),  // index 1 — exceeds stock, must fail
                order(productId, 2));  // index 2 — fits (3 + 2 == 5)

        List<BulkOrderResult> results = bulkOrderService.processBulkOrders(commands);

        assertThat(results).hasSize(3);

        BulkOrderResult first = byIndex(results, 0);
        BulkOrderResult second = byIndex(results, 1);
        BulkOrderResult third = byIndex(results, 2);

        assertThat(first.status()).isEqualTo(BulkOrderResult.Status.SUCCESS);
        assertThat(first.orderId()).isNotNull();

        assertThat(second.status()).isEqualTo(BulkOrderResult.Status.FAILED);
        assertThat(second.error()).containsIgnoringCase("insufficient stock");
        assertThat(second.orderId()).isNull();

        assertThat(third.status()).isEqualTo(BulkOrderResult.Status.SUCCESS);
        assertThat(third.orderId()).isNotNull();

        // The successful orders consumed exactly 3 + 2 = 5; the failed one left nothing behind.
        int remainingStock = productRepository.findById(productId).orElseThrow().getStockQuantity();
        assertThat(remainingStock).isZero();
    }

    private BulkOrderResult byIndex(List<BulkOrderResult> results, int index) {
        return results.stream().filter(r -> r.index() == index).findFirst().orElseThrow();
    }

    private CreateOrderCommand order(Long productId, int quantity) {
        return new CreateOrderCommand(
                1L,
                PaymentMethod.CREDIT_CARD,
                List.of(new CreateOrderCommand.Line(productId, quantity)));
    }
}
