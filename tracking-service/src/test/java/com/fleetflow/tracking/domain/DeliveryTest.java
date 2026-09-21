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

    @Test
    void startRejectsNullDestination() {
        assertThrows(IllegalArgumentException.class,
                () -> Delivery.start(UUID.randomUUID(), UUID.randomUUID(), null, 30.0));
    }
}