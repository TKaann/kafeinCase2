package com.example.orderservice.repository;

import com.example.orderservice.domain.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Fetches a product holding a {@code PESSIMISTIC_WRITE} (SELECT ... FOR UPDATE) lock.
     *
     * <p>This is the core of our race-condition strategy: concurrent orders for the same
     * product serialize on this DB row lock, so stock can never be oversold or go negative.
     * Callers MUST run inside a transaction and should lock products in a consistent order
     * (ascending id) to avoid deadlocks on multi-product orders.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
