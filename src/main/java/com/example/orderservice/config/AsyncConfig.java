package com.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated, bounded thread pool for parallel order processing.
 *
 * <p>We deliberately avoid {@code ForkJoinPool.commonPool()} (the default for
 * {@code CompletableFuture.supplyAsync}): it is a JVM-wide shared resource and our tasks do
 * blocking JDBC work, which could starve it. A bounded pool gives us isolation and back-pressure.
 * {@link ThreadPoolExecutor.CallerRunsPolicy} ensures that under a burst larger than the queue,
 * the submitting thread runs the task itself instead of dropping orders.
 */
@Configuration
public class AsyncConfig {

    public static final String ORDER_EXECUTOR = "orderTaskExecutor";

    @Bean(ORDER_EXECUTOR)
    public Executor orderTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("order-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
