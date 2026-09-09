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
    @Test void historyCannotBeMutatedOutsideAggregate() {
        var order = new Order(UUID.randomUUID(), UUID.randomUUID(), OrderSliceTest.address(),
            OrderSliceTest.address(), "Books", "STANDARD", Instant.now());
        assertThrows(UnsupportedOperationException.class, () -> order.getStatusHistory().clear());
    }
}
