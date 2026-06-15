package com.example.orderservice.domain.model;

import com.example.orderservice.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stockQuantity;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Decreases stock by {@code quantity}, enforcing the core invariant that stock can never
     * go negative. The rule lives on the entity (not the service) so it cannot be bypassed.
     *
     * @throws InsufficientStockException if {@code quantity} exceeds the available stock
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to decrease must be positive: " + quantity);
        }
        if (this.stockQuantity < quantity) {
            throw new InsufficientStockException(this.id, this.name, this.stockQuantity, quantity);
        }
        this.stockQuantity -= quantity;
    }
}
