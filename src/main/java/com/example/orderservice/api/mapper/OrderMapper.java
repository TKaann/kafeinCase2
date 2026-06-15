package com.example.orderservice.api.mapper;

import com.example.orderservice.api.dto.request.CreateOrderRequest;
import com.example.orderservice.api.dto.response.BulkOrderItemResponse;
import com.example.orderservice.api.dto.response.BulkOrderResponse;
import com.example.orderservice.api.dto.response.OrderItemResponse;
import com.example.orderservice.api.dto.response.OrderResponse;
import com.example.orderservice.domain.enums.PaymentStatus;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderItem;
import com.example.orderservice.domain.model.Payment;
import com.example.orderservice.service.BulkOrderResult;
import com.example.orderservice.service.CreateOrderCommand;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        List<CreateOrderCommand.Line> lines = request.items().stream()
                .map(item -> new CreateOrderCommand.Line(item.productId(), item.quantity()))
                .toList();
        return new CreateOrderCommand(request.customerId(), request.paymentMethod(), lines);
    }

    public OrderResponse toResponse(Order order) {
        Payment payment = order.getPayment();
        PaymentStatus paymentStatus = payment != null ? payment.getStatus() : null;
        String transactionReference = payment != null ? payment.getTransactionReference() : null;

        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                paymentStatus,
                transactionReference,
                order.getCreatedAt(),
                items
        );
    }

    public BulkOrderResponse toBulkResponse(List<BulkOrderResult> results) {
        List<BulkOrderItemResponse> items = results.stream()
                .map(r -> new BulkOrderItemResponse(
                        r.index(), r.status().name(), r.orderId(), r.error()))
                .toList();
        int succeeded = (int) results.stream()
                .filter(r -> r.status() == BulkOrderResult.Status.SUCCESS)
                .count();
        return new BulkOrderResponse(results.size(), succeeded, results.size() - succeeded, items);
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
