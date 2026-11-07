package com.storex.order.controller;

import com.storex.order.dto.CreateOrderRequest;
import com.storex.order.entity.StoreOrder;
import com.storex.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreOrder create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{id}")
    public StoreOrder get(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    public List<StoreOrder> list() {
        return orderService.listOrders();
    }
}
