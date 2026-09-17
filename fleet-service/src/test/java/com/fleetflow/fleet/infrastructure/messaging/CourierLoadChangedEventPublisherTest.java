package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierLoadChanged;
import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourierLoadChangedEventPublisherTest {
    @Test void publishesAuthoritativeLoadVersionAndOrderAcknowledgement() {
        var bridge = mock(StreamBridge.class);
        when(bridge.send(eq("courierLoadChanged-out-0"), any(Object.class))).thenReturn(true);
        var courier = Courier.register(VehicleType.VAN, 3, "ZONE_A");
        courier.assignOrder();
        UUID orderId = UUID.randomUUID();
        new CourierLoadChangedEventPublisher(bridge).publish(courier, orderId);
        var event = ArgumentCaptor.forClass(CourierLoadChanged.class);
        verify(bridge).send(eq("courierLoadChanged-out-0"), event.capture());
        assertEquals(courier.getId(), event.getValue().courierId());
        assertEquals(1, event.getValue().activeOrderCount());
        assertEquals(3, event.getValue().capacityCeiling());
        assertEquals(1, event.getValue().loadVersion());
        assertEquals(orderId, event.getValue().acknowledgedOrderId());
    }

    @Test void failedSendIsNotSilentlyAcknowledged() {
        var bridge = mock(StreamBridge.class);
        assertThrows(IllegalStateException.class, () -> new CourierLoadChangedEventPublisher(bridge)
                .publish(Courier.register(VehicleType.VAN, 3, "ZONE_A"), UUID.randomUUID()));
    }
}
