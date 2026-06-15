package com.example.orderservice.service;

import com.example.orderservice.config.AsyncConfig;
import com.example.orderservice.domain.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Processes multiple orders in parallel.
 *
 * <p>Kept as a separate bean from {@link OrderService} on purpose: it calls
 * {@code orderService.createOrder} through the injected Spring proxy, so each order runs in its
 * own {@code @Transactional} boundary. Doing the parallel dispatch inside OrderService itself
 * would be a self-invocation and bypass the transactional proxy.
 *
 * <p>This method is intentionally <strong>not</strong> {@code @Transactional}: transactions are
 * thread-bound, so each async task opens and commits/rolls back its own transaction on an
 * executor thread. That is exactly what gives us per-order isolation — one rollback cannot
 * affect another order.
 */
@Slf4j
@Service
public class BulkOrderService {

    private final OrderService orderService;
    private final Executor executor;

    public BulkOrderService(OrderService orderService,
                            @Qualifier(AsyncConfig.ORDER_EXECUTOR) Executor executor) {
        this.orderService = orderService;
        this.executor = executor;
    }

    public List<BulkOrderResult> processBulkOrders(List<CreateOrderCommand> commands) {
        List<CompletableFuture<BulkOrderResult>> futures = new ArrayList<>(commands.size());
        for (int i = 0; i < commands.size(); i++) {
            final int index = i;
            final CreateOrderCommand command = commands.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> processOne(index, command), executor));
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private BulkOrderResult processOne(int index, CreateOrderCommand command) {
        try {
            Order order = orderService.createOrder(command);
            return BulkOrderResult.success(index, order.getId());
        } catch (Exception ex) {
            // Isolate the failure: capture the reason as a result instead of failing the batch.
            log.warn("Bulk order at index {} failed: {}", index, ex.getMessage());
            return BulkOrderResult.failed(index, ex.getMessage());
        }
    }
}
