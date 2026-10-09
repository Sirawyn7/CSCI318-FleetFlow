package com.fleetflow.tracking;

import com.fleetflow.contracts.tracking.DeliveryCompleted;
import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryCompletedEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.MessageDeliveryException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryCompletedPublisherTest {
    @Test
    void publishesExistingContractAndSurfacesRejectedSends() {
        var bridge = mock(StreamBridge.class);
        var publisher = new DeliveryCompletedEventPublisher(bridge);
        var d = Delivery.start(UUID.randomUUID(), UUID.randomUUID(), new GeoCoordinate(-34, 151), 30);
        d.complete(d.getCourierId(), "Route", "Proof", d.getStartedAt().plusSeconds(60));
        when(bridge.send(eq("deliveryCompleted-out-0"), any(Object.class))).thenReturn(true);
        publisher.publish(d);
        var event = ArgumentCaptor.forClass(DeliveryCompleted.class);
        verify(bridge).send(eq("deliveryCompleted-out-0"), event.capture());
        assertEquals(d.getId(), event.getValue().deliveryId());
        assertEquals(d.getOrderId(), event.getValue().orderId());
        assertEquals(d.getCourierId(), event.getValue().courierId());
        assertEquals(d.getCompletedAt(), event.getValue().completedAt());
        when(bridge.send(eq("deliveryCompleted-out-0"), any(Object.class))).thenReturn(false);
        assertThrows(MessageDeliveryException.class, () -> publisher.publish(d));
    }
}
