package com.example.orderservice;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.exception.InsufficientStockException;
import com.example.orderservice.repository.ProductRepository;
import com.example.orderservice.service.CreateOrderCommand;
import com.example.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core test of this project: under heavy concurrency on a single product, stock must never
 * be oversold or go negative. We release many threads simultaneously with a {@link CountDownLatch}
 * (deterministic, not {@code Thread.sleep}-based) and assert the stock invariant holds exactly.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_REQUESTS = 50;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void stockIsNeverOversold_underConcurrentOrders() throws InterruptedException {
        Product product = productRepository.save(Product.builder()
                .name("Concurrency Test Item")
                .price(new BigDecimal("19.99"))
                .stockQuantity(INITIAL_STOCK)
                .build());
        Long productId = product.getId();

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficientStock = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();                 // all threads block here, then fire together
                    orderService.createOrder(oneUnitOrder(productId));
                    succeeded.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficientStock.incrementAndGet();
                } catch (Exception ignored) {
                    // any other failure is counted by exclusion below
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();                         // release the herd
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).as("all order attempts completed in time").isTrue();

        int remainingStock = productRepository.findById(productId).orElseThrow().getStockQuantity();

        // No oversell: exactly INITIAL_STOCK orders succeed (1 unit each), the rest are rejected.
        assertThat(succeeded.get()).isEqualTo(INITIAL_STOCK);
        assertThat(insufficientStock.get()).isEqualTo(CONCURRENT_REQUESTS - INITIAL_STOCK);
        // Stock never goes negative and lands exactly at zero.
        assertThat(remainingStock).isZero();
        // The invariant: sold units + remaining stock == initial stock.
        assertThat(succeeded.get() + remainingStock).isEqualTo(INITIAL_STOCK);
    }

    private CreateOrderCommand oneUnitOrder(Long productId) {
        return new CreateOrderCommand(
                1L,
                PaymentMethod.CREDIT_CARD,
                List.of(new CreateOrderCommand.Line(productId, 1)));
    }
}
