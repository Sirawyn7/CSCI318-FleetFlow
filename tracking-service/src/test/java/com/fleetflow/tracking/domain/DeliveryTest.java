package com.fleetflow.tracking.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryTest {

    @Test
    void startCreatesADeliveryWithGeneratedId() {
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        GeoCoordinate destination = new GeoCoordinate(-33.8688, 151.2093);

        Delivery delivery = Delivery.start(orderId, courierId, destination, 30.0);

        assertNotNull(delivery.getId());
        assertEquals(orderId, delivery.getOrderId());
        assertEquals(courierId, delivery.getCourierId());
        assertEquals(destination, delivery.getDestination());
        assertEquals(30.0, delivery.getAverageSpeedKmh());
    }

    @Test
    void startRejectsNonPositiveAverageSpeed() {
        assertThrows(IllegalArgumentException.class,
                () -> Delivery.start(UUID.randomUUID(), UUID.randomUUID(), new GeoCoordinate(0, 0), 0));
    }

    private static Delivery started() {
        return Delivery.start(UUID.randomUUID(), UUID.randomUUID(), new GeoCoordinate(-33.8688, 151.2093), 30.0);
    }

    @Test
    void cancellationIsRecordedOnceAndBlocksLaterLifecycleChanges() {
        Delivery delivery = started();
        java.time.Instant at = java.time.Instant.now();
        org.junit.jupiter.api.Assertions.assertTrue(delivery.cancel(at));
        org.junit.jupiter.api.Assertions.assertFalse(delivery.cancel(at.plusSeconds(5)));
        assertEquals(at, delivery.getCancelledAt());
        assertThrows(IllegalStateException.class,
                () -> delivery.complete(delivery.getCourierId(), "route", "proof", at.plusSeconds(10)));
        delivery.arrive(at.plusSeconds(10));
        org.junit.jupiter.api.Assertions.assertNull(delivery.getArrivedAt());
        assertThrows(IllegalStateException.class,
                () -> delivery.reassign(delivery.getCourierId(), UUID.randomUUID(), 20.0));
    }

    @Test
    void arrivedOrCompletedDeliveriesIgnoreALaterCancellation() {
        Delivery arrived = started();
        arrived.arrive(java.time.Instant.now());
        org.junit.jupiter.api.Assertions.assertFalse(arrived.cancel(java.time.Instant.now()));
        org.junit.jupiter.api.Assertions.assertNull(arrived.getCancelledAt());
        Delivery completed = started();
        completed.complete(completed.getCourierId(), "route", "proof", java.time.Instant.now());
        org.junit.jupiter.api.Assertions.assertFalse(completed.cancel(java.time.Instant.now()));
        org.junit.jupiter.api.Assertions.assertNull(completed.getCancelledAt());
    }

    @Test
    void startRejectsNullDestination() {
        assertThrows(IllegalArgumentException.class,
                () -> Delivery.start(UUID.randomUUID(), UUID.randomUUID(), null, 30.0));
    }
}