package com.fleetflow.order.api;

import java.time.Instant;
import java.net.URI;
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
import com.fleetflow.order.domain.OrderItem;
import java.util.List;
import com.fleetflow.order.domain.OrderCancellationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {

        if (request.pickupLatitude() == null || request.pickupLongitude() == null
                || request.dropoffLatitude() == null || request.dropoffLongitude() == null) {
            throw new IllegalArgumentException("Pickup and dropoff coordinates are required");
        }
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

        var order = orderApplicationService.createOrder(
                request.customerId(),
                pickupAddress,
                dropoffAddress,
                request.packageDetails(),
                request.serviceLevel(),
                request.requestedDeliveryBy(),
                request.items() == null ? List.of(new OrderItem(request.packageDetails(), 1))
                        : request.items().stream().map(item -> {
                            if (item == null || item.quantity() == null) {
                                throw new IllegalArgumentException("Each item requires description and quantity");
                            }
                            return new OrderItem(item.description(), item.quantity());
                        }).toList()
        );
        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(OrderResponse.from(order));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        try {
            return orderApplicationService.cancelOrder(id).map(OrderResponse::from)
                    .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (OrderCancellationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {

        return orderApplicationService.getOrder(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record CreateOrderRequest(
            UUID customerId,

            String pickupStreet,
            String pickupSuburb,
            String pickupState,
            String pickupPostcode,
            Double pickupLatitude,
            Double pickupLongitude,

            String dropoffStreet,
            String dropoffSuburb,
            String dropoffState,
            String dropoffPostcode,
            Double dropoffLatitude,
            Double dropoffLongitude,

            String packageDetails,
            String serviceLevel,
            Instant requestedDeliveryBy,
            List<ItemRequest> items
    ) {
    }
    public record ItemRequest(String description, Integer quantity) { }
}