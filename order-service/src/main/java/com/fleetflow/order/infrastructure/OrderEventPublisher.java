package com.fleetflow.order.infrastructure;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.order.OrderCancelled;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.order.application.OrderEventPort;
import com.fleetflow.order.domain.Order;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderEventPublisher implements OrderEventPort {

    private final StreamBridge streamBridge;

    public OrderEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publishOrderPlaced(Order order) {

        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                "OrderPlaced",
                Instant.now(),
                order.getId().toString()
        );

        OrderPlaced event = new OrderPlaced(
                envelope,
                order.getId(),
                order.getCustomerId(),
                new GeoPoint(
                        order.getPickupAddress().latitude(),
                        order.getPickupAddress().longitude()
                ),
                new GeoPoint(
                        order.getDropoffAddress().latitude(),
                        order.getDropoffAddress().longitude()
                ),
                order.getServiceLevel(),
                order.getRequestedDeliveryBy()
        );

        if (!streamBridge.send("orderPlaced-out-0", event)) {
            throw new IllegalStateException("OrderPlaced publication failed");
        }
    }

    @Override
    public void publishOrderCancelled(Order order) {

        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                "OrderCancelled",
                Instant.now(),
                order.getId().toString()
        );

        OrderCancelled event = new OrderCancelled(
                envelope,
                order.getId(),
                "Cancelled by customer"
        );

        if (!streamBridge.send("orderCancelled-out-0", event)) {
            throw new IllegalStateException("OrderCancelled publication failed");
        }
    }
}