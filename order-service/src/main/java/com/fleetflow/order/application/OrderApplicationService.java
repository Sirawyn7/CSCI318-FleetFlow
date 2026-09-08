package com.fleetflow.order.application;

import com.fleetflow.order.domain.Address;
import com.fleetflow.order.domain.Order;
import com.fleetflow.order.domain.OrderRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OrderEventPort orderEventPort;

    public OrderApplicationService(
            OrderRepository orderRepository,
            OrderEventPort orderEventPort) {

        this.orderRepository = orderRepository;
        this.orderEventPort = orderEventPort;
    }

    public Order createOrder(
            UUID customerId,
            Address pickupAddress,
            Address dropoffAddress,
            String packageDetails,
            String serviceLevel,
            Instant requestedDeliveryBy) {

        Order order = new Order(
                UUID.randomUUID(),
                customerId,
                pickupAddress,
                dropoffAddress,
                packageDetails,
                serviceLevel,
                requestedDeliveryBy
        );

        Order savedOrder = orderRepository.save(order);

        orderEventPort.publishOrderPlaced(savedOrder);

        return savedOrder;
    }

    public Optional<Order> getOrder(UUID id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> cancelOrder(UUID id) {

        Optional<Order> existingOrder = orderRepository.findById(id);

        if (existingOrder.isEmpty()) {
            return Optional.empty();
        }

        Order order = existingOrder.get();

        order.cancel();
        orderRepository.save(order);

        orderEventPort.publishOrderCancelled(order);

        return Optional.of(order);
    }
}