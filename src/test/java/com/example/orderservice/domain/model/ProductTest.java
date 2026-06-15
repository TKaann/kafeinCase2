package com.example.orderservice.domain.model;

import com.example.orderservice.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private Product product(int stock) {
        return Product.builder()
                .name("Test Item")
                .price(new BigDecimal("9.99"))
                .stockQuantity(stock)
                .build();
    }

    @Test
    void decreaseStock_reducesQuantity() {
        Product product = product(10);

        product.decreaseStock(3);

        assertThat(product.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void decreaseStock_downToZero_isAllowed() {
        Product product = product(5);

        product.decreaseStock(5);

        assertThat(product.getStockQuantity()).isZero();
    }

    @Test
    void decreaseStock_whenInsufficient_throwsAndLeavesStockUntouched() {
        Product product = product(2);

        assertThatExceptionOfType(InsufficientStockException.class)
                .isThrownBy(() -> product.decreaseStock(5))
                .satisfies(ex -> {
                    assertThat(ex.getAvailable()).isEqualTo(2);
                    assertThat(ex.getRequested()).isEqualTo(5);
                });
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void decreaseStock_withNonPositiveQuantity_throws() {
        Product product = product(10);

        assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.decreaseStock(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
