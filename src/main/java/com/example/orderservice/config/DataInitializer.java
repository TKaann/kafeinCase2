package com.example.orderservice.config;

import com.example.orderservice.domain.model.Product;
import com.example.orderservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the in-memory database with sample products on startup so the API is testable
 * immediately after a single-command run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        List<Product> products = List.of(
                product("Laptop Pro 14", "M3 chip, 16GB RAM, 512GB SSD", "1499.99", 25),
                product("Wireless Mouse", "Ergonomic, silent click", "29.90", 200),
                product("Mechanical Keyboard", "RGB, hot-swappable switches", "89.50", 80),
                product("4K Monitor 27\"", "IPS panel, USB-C", "349.00", 40),
                product("USB-C Hub", "7-in-1 adapter", "45.00", 150),
                product("Noise-Cancelling Headphones", "Over-ear, 30h battery", "199.99", 60),
                product("Webcam 1080p", "Auto-focus, stereo mic", "59.99", 100),
                product("Limited Edition Pen", "Collector item, very low stock", "9.99", 10)
        );

        productRepository.saveAll(products);
        log.info("Seeded {} sample products", products.size());
    }

    private Product product(String name, String description, String price, int stock) {
        return Product.builder()
                .name(name)
                .description(description)
                .price(new BigDecimal(price))
                .stockQuantity(stock)
                .build();
    }
}
