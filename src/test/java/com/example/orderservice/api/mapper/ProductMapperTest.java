package com.example.orderservice.api.mapper;

import com.example.orderservice.api.dto.response.ProductResponse;
import com.example.orderservice.domain.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper();

    @Test
    void toResponse_copiesAllFields() {
        Product product = Product.builder()
                .id(1L).name("Mouse").description("Ergonomic")
                .price(new BigDecimal("29.90")).stockQuantity(200).build();

        ProductResponse response = mapper.toResponse(product);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Mouse");
        assertThat(response.description()).isEqualTo("Ergonomic");
        assertThat(response.price()).isEqualByComparingTo("29.90");
        assertThat(response.stockQuantity()).isEqualTo(200);
    }
}
