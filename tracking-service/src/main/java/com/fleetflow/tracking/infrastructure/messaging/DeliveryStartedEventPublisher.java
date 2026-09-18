package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.tracking.DeliveryStarted;
import com.fleetflow.tracking.domain.Delivery;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DeliveryStartedEventPublisher {

    private static final String BINDING_NAME = "deliveryStarted-out-0";

    private final StreamBridge streamBridge;

    public DeliveryStartedEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publish(Delivery delivery) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                DeliveryStarted.class.getSimpleName(),
                Instant.now(),
                UUID.randomUUID().toString());

        DeliveryStarted event = new DeliveryStarted(envelope, delivery.getId(), delivery.getOrderId(),
                delivery.getCourierId());

        streamBridge.send(BINDING_NAME, event);
    }
}