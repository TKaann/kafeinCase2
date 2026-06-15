package com.example.orderservice.domain.model;

import com.example.orderservice.domain.enums.OrderStatus;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Payment payment;

    // Helper method for bi-directional relationship management
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    /** Attaches the payment, keeping both sides of the bi-directional relationship in sync. */
    public void attachPayment(Payment payment) {
        this.payment = payment;
        payment.setOrder(this);
    }

    /** Recomputes the order total as the sum of item subtotals. */
    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void markConfirmed() {
        this.status = OrderStatus.CONFIRMED;
    }

    public void markFailed() {
        this.status = OrderStatus.FAILED;
    }

    public boolean isPaid() {
        return payment != null && payment.getStatus() == PaymentStatus.COMPLETED;
    }
}
