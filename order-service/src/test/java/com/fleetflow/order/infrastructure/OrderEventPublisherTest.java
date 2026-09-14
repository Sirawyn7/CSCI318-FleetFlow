package com.fleetflow.order.infrastructure;

import com.fleetflow.order.domain.*;
import com.fleetflow.contracts.order.*;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderEventPublisherTest {
    @Test void sendsContractsOnNamedBindingsAndRejectsFailedSends() {
        var bridge = mock(StreamBridge.class);
        var publisher = new OrderEventPublisher(bridge);
        var address = new Address("Street", "Town", "NSW", "2500", -34, 150);
        var order = new Order(UUID.randomUUID(), UUID.randomUUID(), address, address, "Books", "STANDARD", Instant.now());
        when(bridge.send(eq("orderPlaced-out-0"), any())).thenReturn(true);
        publisher.publishOrderPlaced(order);
        verify(bridge).send(eq("orderPlaced-out-0"), argThat(value -> value instanceof OrderPlaced event
            && event.orderId().equals(order.getId())));
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderCancelled(order));
        when(bridge.send(eq("orderCancelled-out-0"), any())).thenReturn(true);
        publisher.publishOrderCancelled(order);
        verify(bridge, times(2)).send(eq("orderCancelled-out-0"), any(OrderCancelled.class));
        when(bridge.send(eq("orderPlaced-out-0"), any())).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderPlaced(order));
        when(bridge.send(eq("orderPlaced-out-0"), any())).thenThrow(new IllegalStateException("broker failed"));
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderPlaced(order));
    }
}
