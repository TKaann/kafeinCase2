package com.example.orderservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    /** Package-private: only the owning {@link Order} wires this back-reference. */
    void setOrder(Order order) {
        this.order = order;
    }

    /**
     * Builds an order item from a product, snapshotting the current unit price so future
     * price changes never alter historical orders. Subtotal is computed here, keeping the
     * money math on the entity rather than scattered across services.
     */
    public static OrderItem of(Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order item quantity must be positive: " + quantity);
        }
        BigDecimal unitPrice = product.getPrice();
        return OrderItem.builder()
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }
}
