package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.dispatch.OrderReassigned;
import com.fleetflow.dispatch.domain.Assignment;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReassignmentPublisherTest {
    @Test void wireEventPreservesTheCommittedTransferIdentityAcrossRetries() {
        var bridge = mock(StreamBridge.class); var publisher = new AssignmentEventPublisher(bridge);
        UUID old = UUID.randomUUID(), replacement = UUID.randomUUID(); Instant time = Instant.now();
        var a = Assignment.create(UUID.randomUUID(), old, time); a.startDelivery(UUID.randomUUID());
        a.attemptException(time); a.reassign(replacement, "SLA_BREACH", "RULE_BASED", time);
        when(bridge.send(eq("orderReassigned-out-0"), any())).thenReturn(false, true);
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderReassigned(a));
        publisher.publishOrderReassigned(a);
        var events = ArgumentCaptor.forClass(OrderReassigned.class);
        verify(bridge, times(2)).send(eq("orderReassigned-out-0"), events.capture());
        assertEquals(events.getAllValues().getFirst(), events.getAllValues().getLast());
        var event = events.getValue();
        assertEquals(a.getReassignmentEventId(), event.envelope().eventId());
        assertEquals(time, event.envelope().occurredAt()); assertEquals("OrderReassigned", event.envelope().eventType());
        assertEquals(old, event.previousCourierId()); assertEquals(replacement, event.newCourierId());
        assertEquals(a.getId(), event.assignmentId()); assertEquals(a.getOrderId(), event.orderId());
        assertEquals("SLA_BREACH", event.reason());
    }
}
