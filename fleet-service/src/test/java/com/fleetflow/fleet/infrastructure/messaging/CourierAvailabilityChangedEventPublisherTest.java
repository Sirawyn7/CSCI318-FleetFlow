package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierAvailabilityChanged;
import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourierAvailabilityChangedEventPublisherTest {
    @Test void deactivatedCourierIsPublishedAsUnavailable() {
        var bridge = mock(StreamBridge.class);
        when(bridge.send(eq("courierAvailabilityChanged-out-0"), any(Object.class))).thenReturn(true);
        var courier = Courier.register(VehicleType.VAN, 3, "ZONE_A");
        courier.changeAvailability(true);
        courier.deactivate();

        new CourierAvailabilityChangedEventPublisher(bridge).publish(courier);

        var event = ArgumentCaptor.forClass(CourierAvailabilityChanged.class);
        verify(bridge).send(eq("courierAvailabilityChanged-out-0"), event.capture());
        assertEquals(courier.getId(), event.getValue().courierId());
        assertFalse(event.getValue().available());
    }

    @Test void failedSendIsNotSilentlyAcknowledged() {
        var bridge = mock(StreamBridge.class);
        assertThrows(org.springframework.messaging.MessageDeliveryException.class, () -> new CourierAvailabilityChangedEventPublisher(bridge)
                .publish(Courier.register(VehicleType.VAN, 3, "ZONE_A")));
    }
}
