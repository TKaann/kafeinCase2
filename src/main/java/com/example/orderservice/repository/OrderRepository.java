package com.example.orderservice.repository;

import com.example.orderservice.domain.model.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Loads an order together with its items and payment in a single query, avoiding
     * LazyInitializationException when the detail is serialized outside the transaction.
     */
    @EntityGraph(attributePaths = {"items", "items.product", "payment"})
    Optional<Order> findWithDetailsById(Long id);
}
