package com.example.orderservice.api.controller;

import com.example.orderservice.api.dto.request.BulkOrderRequest;
import com.example.orderservice.api.dto.request.CreateOrderRequest;
import com.example.orderservice.api.dto.response.BulkOrderResponse;
import com.example.orderservice.api.dto.response.OrderResponse;
import com.example.orderservice.api.mapper.OrderMapper;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.service.BulkOrderService;
import com.example.orderservice.service.CreateOrderCommand;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final BulkOrderService bulkOrderService;
    private final OrderMapper orderMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(orderMapper.toCommand(request));
        return orderMapper.toResponse(order);
    }

    /**
     * Processes multiple orders in parallel. Returns 200 with a per-order result list: a single
     * failed order is reported in its result entry and never aborts the others.
     */
    @PostMapping("/bulk")
    public BulkOrderResponse createBulkOrders(@Valid @RequestBody BulkOrderRequest request) {
        List<CreateOrderCommand> commands = request.orders().stream()
                .map(orderMapper::toCommand)
                .toList();
        return orderMapper.toBulkResponse(bulkOrderService.processBulkOrders(commands));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderMapper.toResponse(orderService.getOrderDetail(id));
    }
}
