package com.fleetflow.tracking;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryCompletionTest {
    @Test
    void proofIsImmutableAndIdenticalRetryPreservesTimestamp() {
        var courier = UUID.randomUUID();
        var delivery = Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-34, 151), 30);
        var completed = delivery.getStartedAt().plusSeconds(60);
        assertTrue(delivery.complete(courier, " A to B ", " Received by Alex ", completed));
        assertFalse(delivery.complete(courier, "A to B", "Received by Alex", completed.plusSeconds(20)));
        assertEquals(completed, delivery.getCompletedAt());
        assertThrows(IllegalStateException.class,
                () -> delivery.complete(courier, "A to B", "Different proof", completed));
        assertEquals("Received by Alex", delivery.getConfirmation());
    }

    @Test
    void rejectsWrongCourierMissingProofAndInvalidTimeWithoutMutation() {
        var courier = UUID.randomUUID();
        var delivery = Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-34, 151), 30);
        var time = delivery.getStartedAt();
        assertThrows(IllegalStateException.class,
                () -> delivery.complete(UUID.randomUUID(), "Route", "Proof", time));
        assertThrows(IllegalArgumentException.class, () -> delivery.complete(courier, " ", "Proof", time));
        assertThrows(IllegalArgumentException.class, () -> delivery.complete(courier, "Route", null, time));
        assertThrows(IllegalArgumentException.class,
                () -> delivery.complete(courier, "Route", "x".repeat(256), time));
        assertThrows(IllegalArgumentException.class,
                () -> delivery.complete(courier, "Route", "Proof", time.minusSeconds(1)));
        assertNull(delivery.getCompletedAt());
    }
}
