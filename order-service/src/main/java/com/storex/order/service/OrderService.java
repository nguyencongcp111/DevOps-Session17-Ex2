package com.storex.order.service;

import com.storex.order.client.UserClient;
import com.storex.order.dto.CreateOrderRequest;
import com.storex.order.entity.StoreOrder;
import com.storex.order.repository.StoreOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final StoreOrderRepository orderRepository;
    private final UserClient userClient;

    public OrderService(StoreOrderRepository orderRepository, UserClient userClient) {
        this.orderRepository = orderRepository;
        this.userClient = userClient;
    }

    public StoreOrder createOrder(CreateOrderRequest request) {
        log.info("Creating order userId={} product={} quantity={}",
                request.userId(), request.productName(), request.quantity());

        boolean userExists;
        try {
            userExists = userClient.userExists(request.userId());
        } catch (RestClientException e) {
            // Truyen exception lam tham so cuoi de stack trace vao field stack_trace trong JSON
            log.error("Cannot reach user-service while validating userId={}", request.userId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service is unavailable");
        }

        if (!userExists) {
            log.warn("Order rejected, user not found userId={}", request.userId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + request.userId());
        }

        StoreOrder saved = orderRepository.save(new StoreOrder(
                request.userId(), request.productName(), request.quantity(), request.amount()));
        log.info("Order created id={} userId={} amount={}", saved.getId(), saved.getUserId(), saved.getAmount());
        return saved;
    }

    public StoreOrder getOrder(Long id) {
        log.debug("Fetching order id={}", id);
        return orderRepository.findById(id).orElseThrow(() -> {
            log.warn("Order not found id={}", id);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id);
        });
    }

    public List<StoreOrder> listOrders() {
        log.debug("Listing all orders");
        return orderRepository.findAllByOrderByIdDesc();
    }
}
