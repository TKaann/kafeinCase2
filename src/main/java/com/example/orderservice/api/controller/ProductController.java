package com.example.orderservice.api.controller;

import com.example.orderservice.api.dto.request.UpdateStockRequest;
import com.example.orderservice.api.dto.response.ProductResponse;
import com.example.orderservice.api.mapper.ProductMapper;
import com.example.orderservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @GetMapping
    public List<ProductResponse> listProducts() {
        return productService.findAll().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productMapper.toResponse(productService.getById(id));
    }

    /** Sets a product's stock to an absolute value. Test-harness helper for resetting state. */
    @PatchMapping("/{id}/stock")
    public ProductResponse updateStock(@PathVariable Long id, @Valid @RequestBody UpdateStockRequest request) {
        return productMapper.toResponse(productService.setStock(id, request.stockQuantity()));
    }
}
