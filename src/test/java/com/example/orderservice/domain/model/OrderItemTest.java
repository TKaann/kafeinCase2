package com.example.orderservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    private Product product(String price) {
        return Product.builder()
                .name("Test Item")
                .price(new BigDecimal(price))
                .stockQuantity(100)
                .build();
    }

    @Test
    void of_snapshotsUnitPriceAndComputesSubtotal() {
        Product product = product("19.99");

        OrderItem item = OrderItem.of(product, 3);

        assertThat(item.getProduct()).isSameAs(product);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("19.99");
        assertThat(item.getSubtotal()).isEqualByComparingTo("59.97");
    }

    @Test
    void of_withNonPositiveQuantity_throws() {
        Product product = product("10.00");

        assertThatThrownBy(() -> OrderItem.of(product, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
