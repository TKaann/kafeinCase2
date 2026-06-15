package com.example.orderservice.api.mapper;

import com.example.orderservice.api.dto.request.CreateOrderRequest;
import com.example.orderservice.api.dto.request.OrderItemRequest;
import com.example.orderservice.api.dto.response.BulkOrderResponse;
import com.example.orderservice.api.dto.response.OrderResponse;
import com.example.orderservice.domain.enums.OrderStatus;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.domain.enums.PaymentStatus;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderItem;
import com.example.orderservice.domain.model.Payment;
import com.example.orderservice.domain.model.Product;
import com.example.orderservice.service.BulkOrderResult;
import com.example.orderservice.service.CreateOrderCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    @Test
    void toResponse_mapsOrderItemsAndPayment() {
        Product product = Product.builder()
                .id(1L).name("Laptop").price(new BigDecimal("100.00")).stockQuantity(5).build();
        Order order = Order.builder()
                .id(10L).customerId(7L).status(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.CREDIT_CARD).totalAmount(BigDecimal.ZERO).build();
        order.addItem(OrderItem.of(product, 2));
        order.recalculateTotal();
        order.attachPayment(Payment.builder()
                .status(PaymentStatus.COMPLETED).paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(new BigDecimal("200.00")).transactionReference("CC-1").build());

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.customerId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.transactionReference()).isEqualTo("CC-1");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(1L);
        assertThat(response.items().get(0).productName()).isEqualTo("Laptop");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void toResponse_withoutPayment_leavesPaymentFieldsNull() {
        Order order = Order.builder()
                .id(11L).customerId(1L).status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.CRYPTO).totalAmount(BigDecimal.ZERO).build();

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.paymentStatus()).isNull();
        assertThat(response.transactionReference()).isNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void toCommand_mapsRequestToServiceCommand() {
        CreateOrderRequest request = new CreateOrderRequest(
                5L, PaymentMethod.BANK_TRANSFER, List.of(new OrderItemRequest(3L, 4)));

        CreateOrderCommand command = mapper.toCommand(request);

        assertThat(command.customerId()).isEqualTo(5L);
        assertThat(command.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(command.lines()).hasSize(1);
        assertThat(command.lines().get(0).productId()).isEqualTo(3L);
        assertThat(command.lines().get(0).quantity()).isEqualTo(4);
    }

    @Test
    void toBulkResponse_countsSucceededAndFailed() {
        List<BulkOrderResult> results = List.of(
                BulkOrderResult.success(0, 100L),
                BulkOrderResult.failed(1, "insufficient stock"),
                BulkOrderResult.success(2, 101L));

        BulkOrderResponse response = mapper.toBulkResponse(results);

        assertThat(response.total()).isEqualTo(3);
        assertThat(response.succeeded()).isEqualTo(2);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(1).status()).isEqualTo("FAILED");
        assertThat(response.results().get(1).error()).isEqualTo("insufficient stock");
        assertThat(response.results().get(1).orderId()).isNull();
    }
}
