package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.fleet.CourierLoadChanged;
import com.fleetflow.fleet.domain.Courier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class CourierLoadChangedEventPublisher {
    private final StreamBridge bridge;
    public CourierLoadChangedEventPublisher(StreamBridge bridge) { this.bridge = bridge; }

    public void publish(Courier courier, UUID orderId) {
        var envelope = new EventEnvelope(UUID.randomUUID(), "CourierLoadChanged", Instant.now(), orderId.toString());
        var event = new CourierLoadChanged(envelope, courier.getId(), courier.getActiveOrderCount(),
                courier.getCapacity(), courier.getLoadVersion(), orderId);
        if (!bridge.send("courierLoadChanged-out-0", event)) {
            throw new IllegalStateException("Could not publish courier load change");
        }
    }
}
