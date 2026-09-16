package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.fleet.domain.Courier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * FR10 producer. Translates the domain GeoCoordinate into the wire-only
 * contracts.common.GeoPoint at this boundary -- the two types are deliberately distinct
 * (see GeoCoordinate's Javadoc), so this is the one place that maps between them.
 */

@Component
public class CourierLocationUpdatedEventPublisher {
    private static final String BINDING_NAME = "courierLocationUpdated-out-0";
    private final StreamBridge streamBridge;

    public CourierLocationUpdatedEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publish(Courier courier) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                CourierLocationUpdated.class.getSimpleName(),
                Instant.now(),
                UUID.randomUUID().toString()
        );

        GeoPoint location = new GeoPoint(
                courier.getCurrentLocation().latitude(),
                courier.getCurrentLocation().longitude()
        );

        CourierLocationUpdated event = new CourierLocationUpdated(
                envelope,
                courier.getId(),
                location,
                courier.getLocationUpdatedAt()
        );
        streamBridge.send(BINDING_NAME, event);
    }
}
