package com.example.orderservice.exception;

/**
 * Thrown when an order requests more units of a product than are currently in stock.
 * Unchecked on purpose: {@code @Transactional} rolls back on unchecked exceptions by
 * default, so we never risk a silent "forgot rollbackFor" bug.
 */
public class InsufficientStockException extends RuntimeException {

    private final Long productId;
    private final int available;
    private final int requested;

    public InsufficientStockException(Long productId, String productName, int available, int requested) {
        super("Insufficient stock for product '" + productName + "' (id=" + productId
                + "): available=" + available + ", requested=" + requested);
        this.productId = productId;
        this.available = available;
        this.requested = requested;
    }

    public Long getProductId() {
        return productId;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
