package com.example.orderservice.service;

import com.example.orderservice.domain.model.Product;
import com.example.orderservice.exception.ProductNotFoundException;
import com.example.orderservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public Product setStock(Long productId, int newQuantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.changeStockTo(newQuantity);
        return product; // dirty checking flushes the change on commit
    }

    /**
     * Fetches a product with a pessimistic write lock for the duration of the current
     * transaction. Must be called from within a transaction (e.g. order creation); the lock
     * serializes concurrent stock updates for the same product, preventing overselling.
     */
    public Product getForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
