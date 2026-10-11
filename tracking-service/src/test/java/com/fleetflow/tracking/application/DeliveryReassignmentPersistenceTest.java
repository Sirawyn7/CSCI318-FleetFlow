package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.*;
import com.fleetflow.tracking.infrastructure.persistence.*;
import com.fleetflow.tracking.infrastructure.messaging.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest
@Import({DeliveryReassignmentService.class, VehicleSpeedConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryReassignmentPersistenceTest {
    @Autowired DeliveryReassignmentService service;
    @Autowired DeliveryJpaRepository deliveries;
    @Autowired CourierVehicleTypeViewRepository couriers;
    @Autowired ProcessedEventRepository processed;
    @MockitoBean ActiveDeliverySnapshotPublisher snapshots;
    private UUID courier(String vehicle) {
        UUID id = UUID.randomUUID(); couriers.save(new CourierVehicleTypeView(id, vehicle)); return id;
    }
    @Test void sameDeliveryMovesBetweenCourierSnapshotsAndDuplicateIsIgnored() {
        UUID old = courier("VAN"), replacement = courier("BICYCLE");
        var d = deliveries.save(Delivery.start(UUID.randomUUID(), old, new GeoCoordinate(-34.4, 150.8), 28));
        UUID event = UUID.randomUUID(); service.reassign(event, d.getOrderId(), old, replacement);
        service.reassign(event, d.getOrderId(), old, replacement);
        var updated = deliveries.findById(d.getId()).orElseThrow();
        assertEquals(replacement, updated.getCourierId());
        assertEquals(d.getStartedAt().toEpochMilli(), updated.getStartedAt().toEpochMilli());
        assertTrue(deliveries.findByCourierIdAndArrivedAtIsNullAndCompletedAtIsNull(old).isEmpty());
        assertEquals(d.getId(), deliveries.findByCourierIdAndArrivedAtIsNullAndCompletedAtIsNull(replacement).getFirst().getId());
        verify(snapshots).publish(old); verify(snapshots).publish(replacement);
        assertThrows(IllegalStateException.class, () -> updated.complete(old, "route", "proof", java.time.Instant.now()));
        assertTrue(updated.complete(replacement, "route", "proof", java.time.Instant.now()));
    }
    @Test void snapshotFailureRollsBackDeliveryAndDeduplication() {
        UUID old = courier("VAN"), replacement = courier("BICYCLE");
        var d = deliveries.save(Delivery.start(UUID.randomUUID(), old, new GeoCoordinate(-34.4, 150.8), 28));
        UUID event = UUID.randomUUID(); doThrow(new IllegalStateException("Broker down")).when(snapshots).publish(replacement);
        assertThrows(IllegalStateException.class, () -> service.reassign(event, d.getOrderId(), old, replacement));
        assertEquals(old, deliveries.findById(d.getId()).orElseThrow().getCourierId()); assertFalse(processed.existsById(event));
        doNothing().when(snapshots).publish(replacement);
        service.reassign(event, d.getOrderId(), old, replacement);
        assertEquals(replacement, deliveries.findById(d.getId()).orElseThrow().getCourierId());
    }
    @Test void completedDeliveryCannotBeReopenedAndMissingBootstrapRetries() {
        UUID old = courier("VAN"), replacement = courier("BICYCLE");
        var d = Delivery.start(UUID.randomUUID(), old, new GeoCoordinate(-34.4, 150.8), 28);
        d.complete(old, "route", "proof", java.time.Instant.now()); deliveries.save(d);
        service.reassign(UUID.randomUUID(), d.getOrderId(), old, replacement);
        assertEquals(old, deliveries.findById(d.getId()).orElseThrow().getCourierId());
        assertThrows(IllegalStateException.class, () -> service.reassign(UUID.randomUUID(), UUID.randomUUID(), old, replacement));
    }
}
