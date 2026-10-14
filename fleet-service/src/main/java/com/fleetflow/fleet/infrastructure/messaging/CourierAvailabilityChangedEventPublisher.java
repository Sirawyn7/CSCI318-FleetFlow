package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.fleet.CourierAvailabilityChanged;
import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.CourierStatus;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class CourierAvailabilityChangedEventPublisher {
    private static final String BINDING_NAME = "courierAvailabilityChanged-out-0";
    private final StreamBridge streamBridge;

    public CourierAvailabilityChangedEventPublisher(StreamBridge streamBridge) {
        this .streamBridge = streamBridge;
    }

    public void publish(Courier courier) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                CourierAvailabilityChanged.class.getSimpleName(),
                Instant.now(),
                UUID.randomUUID().toString()
        );

        CourierAvailabilityChanged event = new CourierAvailabilityChanged(
                envelope,
                courier.getId(),
                courier.getStatus() == CourierStatus.AVAILABLE
        );
        if (!streamBridge.send(BINDING_NAME, event)) {
            // Not IllegalStateException: the API maps that to 409, and a broker failure isn't a conflict.
            throw new org.springframework.messaging.MessageDeliveryException("CourierAvailabilityChanged publication failed");
        }
    }
}
