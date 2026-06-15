package com.example.orderservice;

import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.repository.ProductRepository;
import com.example.orderservice.service.CreateOrderCommand;
import com.example.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies deadlock-freedom of the ordered-locking strategy. Two threads groups place orders for
 * the same two products but list the items in opposite order ([A,B] vs [B,A]), repeatedly and
 * concurrently. Because {@code OrderService} always locks products in ascending id order, no
 * lock-acquisition cycle can form. The test asserts every order completes (no deadlock/timeout)
 * and that the final stock is exactly consistent with the number of orders placed.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderedLockingConcurrencyTest {

    private static final int THREADS_PER_GROUP = 10;
    private static final int ITERATIONS = 5;
    private static final int INITIAL_STOCK = 1000;

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void oppositeItemOrdering_doesNotDeadlock_andKeepsStockConsistent() throws InterruptedException {
        Product a = productRepository.save(Product.builder()
                .name("Lock Test A").price(new BigDecimal("10.00")).stockQuantity(INITIAL_STOCK).build());
        Product b = productRepository.save(Product.builder()
                .name("Lock Test B").price(new BigDecimal("20.00")).stockQuantity(INITIAL_STOCK).build());

        int totalThreads = THREADS_PER_GROUP * 2;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalThreads);
        AtomicInteger succeeded = new AtomicInteger();
        Queue<String> errors = new ConcurrentLinkedQueue<>();

        // Group 1 lists items [A, B]; group 2 lists them [B, A]. Internal sort makes the actual
        // lock order identical, which is exactly what prevents a deadlock.
        for (int g = 0; g < totalThreads; g++) {
            boolean abOrder = g < THREADS_PER_GROUP;
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        orderService.createOrder(abOrder ? order(a, b) : order(b, a));
                        succeeded.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).as("all orders completed without deadlock/timeout").isTrue();
        assertThat(errors).as("no order failed unexpectedly").isEmpty();

        int totalOrders = totalThreads * ITERATIONS;
        assertThat(succeeded.get()).isEqualTo(totalOrders);
        // Each order consumes one unit of each product.
        assertThat(productRepository.findById(a.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - totalOrders);
        assertThat(productRepository.findById(b.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - totalOrders);
    }

    private CreateOrderCommand order(Product first, Product second) {
        return new CreateOrderCommand(
                1L, PaymentMethod.CREDIT_CARD,
                List.of(new CreateOrderCommand.Line(first.getId(), 1),
                        new CreateOrderCommand.Line(second.getId(), 1)));
    }
}
