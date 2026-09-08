package com.fleetflow.order.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fleetflow.order.application.OrderApplicationService;
import com.fleetflow.order.domain.Address;
import com.fleetflow.order.domain.Order;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @PostMapping
    public Order createOrder(@RequestBody CreateOrderRequest request) {

        Address pickupAddress = new Address(
                request.pickupStreet(),
                request.pickupSuburb(),
                request.pickupState(),
                request.pickupPostcode(),
                request.pickupLatitude(),
                request.pickupLongitude()
        );

        Address dropoffAddress = new Address(
                request.dropoffStreet(),
                request.dropoffSuburb(),
                request.dropoffState(),
                request.dropoffPostcode(),
                request.dropoffLatitude(),
                request.dropoffLongitude()
        );

        return orderApplicationService.createOrder(
                request.customerId(),
                pickupAddress,
                dropoffAddress,
                request.packageDetails(),
                request.serviceLevel(),
                request.requestedDeliveryBy()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable UUID id) {

        return orderApplicationService.getOrder(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record CreateOrderRequest(
            UUID customerId,

            String pickupStreet,
            String pickupSuburb,
            String pickupState,
            String pickupPostcode,
            double pickupLatitude,
            double pickupLongitude,

            String dropoffStreet,
            String dropoffSuburb,
            String dropoffState,
            String dropoffPostcode,
            double dropoffLatitude,
            double dropoffLongitude,

            String packageDetails,
            String serviceLevel,
            Instant requestedDeliveryBy
    ) {
    }
}