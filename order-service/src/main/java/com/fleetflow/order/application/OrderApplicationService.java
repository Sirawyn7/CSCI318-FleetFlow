package com.fleetflow.order.application;

import com.fleetflow.order.domain.Address;
import com.fleetflow.order.domain.Order;
import com.fleetflow.order.domain.OrderItem;
import com.fleetflow.order.domain.OrderStatus;
import java.util.List;
import com.fleetflow.order.domain.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactions roll back order and history writes when publication throws.
 * Kafka and JPA are not atomic: a later database commit failure can still leave an
 * emitted event. A transactional outbox would be a separate reliability change.
 */
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

    @Transactional
    public Order createOrder(
            UUID customerId,
            Address pickupAddress,
            Address dropoffAddress,
            String packageDetails,
            String serviceLevel,
            Instant requestedDeliveryBy) {

        return placeOrder(customerId, pickupAddress, dropoffAddress, packageDetails, serviceLevel,
                requestedDeliveryBy, List.of(new OrderItem(packageDetails, 1)));
    }

    @Transactional
    public Order createOrder(UUID customerId, Address pickupAddress, Address dropoffAddress,
                             String packageDetails, String serviceLevel, Instant requestedDeliveryBy,
                             List<OrderItem> items) {
        return placeOrder(customerId, pickupAddress, dropoffAddress, packageDetails, serviceLevel,
                requestedDeliveryBy, items);
    }

    private Order placeOrder(UUID customerId, Address pickupAddress, Address dropoffAddress,
                             String packageDetails, String serviceLevel, Instant requestedDeliveryBy,
                             List<OrderItem> items) {
        Order order = new Order(
                UUID.randomUUID(),
                customerId,
                pickupAddress,
                dropoffAddress,
                packageDetails,
                serviceLevel,
                requestedDeliveryBy,
                items
        );

        Order savedOrder = orderRepository.save(order);

        orderEventPort.publishOrderPlaced(savedOrder);

        return savedOrder;
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrder(UUID id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Optional<Order> cancelOrder(UUID id) {

        Optional<Order> existingOrder = orderRepository.findByIdForUpdate(id);

        if (existingOrder.isEmpty()) {
            return Optional.empty();
        }

        Order order = existingOrder.get();

        order.cancel();
        orderRepository.save(order);

        orderEventPort.publishOrderCancelled(order);

        return Optional.of(order);
    }

    /**
     * Records a lifecycle change reported by another context. Takes the same row lock as
     * cancellation, so a concurrent cancel and assignment are applied one after the other.
     * A missing order is thrown rather than skipped: Order publishes OrderPlaced before its
     * own commit, so a very fast downstream event can arrive first and must be retried.
     */
    @Transactional
    public void recordLifecycle(UUID orderId, OrderStatus status, Instant at) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found yet: " + orderId));
        if (order.advanceTo(status, at)) {
            orderRepository.save(order);
        }
    }
}