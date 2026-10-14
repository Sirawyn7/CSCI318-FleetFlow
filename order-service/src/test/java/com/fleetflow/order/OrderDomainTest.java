package com.fleetflow.order;

import com.fleetflow.order.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrderDomainTest {
    @Test void validatesRequiredOrderFields() {
        assertThrows(IllegalArgumentException.class, () -> new Order(UUID.randomUUID(), null,
            OrderSliceTest.address(), OrderSliceTest.address(), "Books", "STANDARD", Instant.now()));
    }
    @Test void rejectsInvalidCoordinatesAndBlankAddresses() {
        assertThrows(IllegalArgumentException.class, () -> new Address("", "Town", "NSW", "2500", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Address("Street", "Town", "NSW", "2500", Double.NaN, 0));
    }
    @Test void preventsCancellationAfterDispatch() {
        for (var status : OrderStatus.values()) {
            if (status == OrderStatus.PLACED) continue;
            var order = new Order(UUID.randomUUID(), UUID.randomUUID(), OrderSliceTest.address(),
                OrderSliceTest.address(), "Books", "STANDARD", Instant.now());
            org.springframework.test.util.ReflectionTestUtils.setField(order, "status", status);
            assertThrows(OrderCancellationException.class, order::cancel);
            assertEquals(status, order.getStatus());
        }
    }
    private static Order placed() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), OrderSliceTest.address(),
            OrderSliceTest.address(), "Books", "STANDARD", Instant.now());
    }
    @Test void lifecycleAdvancesThroughEachStageWithTheReportedTimes() {
        var order = placed();
        var t = Instant.parse("2026-10-14T01:00:00Z");
        assertTrue(order.advanceTo(OrderStatus.ASSIGNED, t));
        assertTrue(order.advanceTo(OrderStatus.IN_TRANSIT, t.plusSeconds(60)));
        assertTrue(order.advanceTo(OrderStatus.DELIVERED, t.plusSeconds(600)));
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(java.util.List.of(OrderStatus.PLACED, OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED),
            order.getStatusHistory().stream().map(OrderStatusChange::status).toList());
        assertEquals(t.plusSeconds(60), order.getStatusHistory().get(2).changedAt());
    }
    @Test void duplicateAndLateEventsDoNotChangeStatusOrHistory() {
        var order = placed();
        var t = Instant.now();
        order.advanceTo(OrderStatus.IN_TRANSIT, t);
        assertFalse(order.advanceTo(OrderStatus.IN_TRANSIT, t));
        assertFalse(order.advanceTo(OrderStatus.ASSIGNED, t.minusSeconds(30)));
        assertEquals(OrderStatus.IN_TRANSIT, order.getStatus());
        assertEquals(2, order.getStatusHistory().size());
    }
    @Test void failureOnlyAppliesBeforeAssignment() {
        var failed = placed();
        assertTrue(failed.advanceTo(OrderStatus.FAILED, Instant.now()));
        assertFalse(failed.advanceTo(OrderStatus.ASSIGNED, Instant.now()));
        assertEquals(OrderStatus.FAILED, failed.getStatus());
        var assigned = placed();
        assigned.advanceTo(OrderStatus.ASSIGNED, Instant.now());
        assertFalse(assigned.advanceTo(OrderStatus.FAILED, Instant.now()));
        assertEquals(OrderStatus.ASSIGNED, assigned.getStatus());
    }
    @Test void cancelledAndDeliveredOrdersIgnoreLaterEvents() {
        var cancelled = placed();
        cancelled.cancel();
        for (var status : java.util.List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED, OrderStatus.FAILED)) {
            assertFalse(cancelled.advanceTo(status, Instant.now()));
        }
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(2, cancelled.getStatusHistory().size());
        var delivered = placed();
        delivered.advanceTo(OrderStatus.DELIVERED, Instant.now());
        assertFalse(delivered.advanceTo(OrderStatus.FAILED, Instant.now()));
        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
    }
    @Test void onlyExternallyReportedStatusesAreAccepted() {
        var order = placed();
        assertThrows(IllegalArgumentException.class, () -> order.advanceTo(OrderStatus.PLACED, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> order.advanceTo(OrderStatus.CANCELLED, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> order.advanceTo(OrderStatus.ASSIGNED, null));
    }
    @Test void historyCannotBeMutatedOutsideAggregate() {
        var order = new Order(UUID.randomUUID(), UUID.randomUUID(), OrderSliceTest.address(),
            OrderSliceTest.address(), "Books", "STANDARD", Instant.now());
        assertThrows(UnsupportedOperationException.class, () -> order.getStatusHistory().clear());
    }
}
