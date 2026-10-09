package com.fleetflow.tracking.infrastructure.persistence;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase
class DeliveryStatusPersistenceTest {

    @Autowired DeliveryJpaRepository deliveries;
    @Autowired DeliveryEtaViewRepository etaViews;

    @Test
    void findsTheDeliveryForAnOrder() {
        UUID orderId = UUID.randomUUID();
        Delivery saved = deliveries.saveAndFlush(
                Delivery.start(orderId, UUID.randomUUID(), new GeoCoordinate(-34.4, 150.9), 30.0));

        var found = deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId);

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void unknownOrderHasNoDelivery() {
        assertTrue(deliveries.findFirstByOrderIdOrderByStartedAtDesc(UUID.randomUUID()).isEmpty());
    }

    @Test
    void etaViewRoundTrips() {
        UUID deliveryId = UUID.randomUUID();
        Instant eta = Instant.parse("2026-10-07T08:10:00Z");
        etaViews.saveAndFlush(new DeliveryEtaView(deliveryId, eta, Instant.parse("2026-10-07T07:55:00Z")));

        assertEquals(eta, etaViews.findById(deliveryId).orElseThrow().getEstimatedArrival());
    }
}