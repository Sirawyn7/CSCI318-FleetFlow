package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.tracking.DeliveryCompleted;
import com.fleetflow.tracking.domain.Delivery;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class DeliveryCompletedEventPublisher {
    private final StreamBridge streamBridge;

    public DeliveryCompletedEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publish(Delivery delivery) {
        var envelope = new EventEnvelope(UUID.randomUUID(), "DeliveryCompleted",
                delivery.getCompletedAt(), delivery.getId().toString());
        var event = new DeliveryCompleted(envelope, delivery.getId(), delivery.getOrderId(),
                delivery.getCourierId(), delivery.getCompletedAt());
        if (!streamBridge.send("deliveryCompleted-out-0", event)) {
            throw new org.springframework.messaging.MessageDeliveryException("DeliveryCompleted publication failed");
        }
    }
}
