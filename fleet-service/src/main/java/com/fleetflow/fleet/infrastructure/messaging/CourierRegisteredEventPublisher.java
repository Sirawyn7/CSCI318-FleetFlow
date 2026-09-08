package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.fleet.domain.Courier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Producer bean (docs/decisions/0004): the only place in this service that constructs a
 * com.fleetflow.contracts type. Binding name "courierRegistered-out-0" is configured in
 * application.yml under spring.cloud.stream.bindings — convention: <event>-out-0.
 */
@Component
public class CourierRegisteredEventPublisher {

    private static final String BINDING_NAME = "courierRegistered-out-0";

    private final StreamBridge streamBridge;

    public CourierRegisteredEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publish(Courier courier) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                CourierRegistered.class.getSimpleName(),
                Instant.now(),
                UUID.randomUUID().toString());

        CourierRegistered event = new CourierRegistered(
                envelope,
                courier.getId(),
                courier.getVehicleType().name(),
                courier.getCapacity(),
                courier.getOperatingZone());

        streamBridge.send(BINDING_NAME, event);
    }
}
