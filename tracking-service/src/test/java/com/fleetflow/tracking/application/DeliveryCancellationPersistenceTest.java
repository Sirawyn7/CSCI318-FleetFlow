package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryStartedEventPublisher;
import com.fleetflow.tracking.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({DeliveryCancellationService.class, DeliveryBootstrapService.class, DeliveryReassignmentService.class,
        VehicleSpeedConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryCancellationPersistenceTest {
    @Autowired DeliveryCancellationService cancellation;
    @Autowired DeliveryBootstrapService bootstrap;
    @Autowired DeliveryReassignmentService reassignment;
    @Autowired DeliveryJpaRepository deliveries;
    @Autowired CourierVehicleTypeViewRepository couriers;
    @Autowired OrderDestinationViewRepository destinations;
    @Autowired OrderCancellationStateRepository states;
    @MockitoBean ActiveDeliverySnapshotPublisher snapshots;
    @MockitoBean DeliveryStartedEventPublisher started;

    private UUID courier() {
        UUID id = UUID.randomUUID();
        couriers.save(new CourierVehicleTypeView(id, "VAN"));
        return id;
    }

    private UUID placedOrder() {
        UUID id = UUID.randomUUID();
        destinations.save(new OrderDestinationView(id, new GeoCoordinate(-34.4, 150.8), Instant.now().plusSeconds(3600)));
        return id;
    }

    private java.util.List<Delivery> active(UUID courierId) {
        return deliveries.findByCourierIdAndArrivedAtIsNullAndCompletedAtIsNullAndCancelledAtIsNull(courierId);
    }

    @Test void cancellingAnActiveDeliveryStopsMonitoringOnce() {
        UUID courierId = courier(), orderId = placedOrder();
        bootstrap.bootstrap(UUID.randomUUID(), "OrderAssigned", orderId, courierId);
        assertEquals(1, active(courierId).size());
        reset(snapshots);

        UUID event = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-14T02:00:00Z");
        cancellation.cancel(event, "OrderCancelled", orderId, at);
        cancellation.cancel(event, "OrderCancelled", orderId, at);
        cancellation.cancel(UUID.randomUUID(), "OrderCancelled", orderId, at.plusSeconds(5));

        assertTrue(active(courierId).isEmpty());
        assertEquals(at, deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId).orElseThrow().getCancelledAt());
        verify(snapshots, times(1)).publish(courierId);
    }

    @Test void cancellationBeforeAssignmentPreventsTheDeliveryFromStarting() {
        UUID courierId = courier(), orderId = placedOrder();
        cancellation.cancel(UUID.randomUUID(), "OrderCancelled", orderId, Instant.now());
        assertTrue(states.findById(orderId).orElseThrow().isCancelled());

        bootstrap.bootstrap(UUID.randomUUID(), "OrderAssigned", orderId, courierId);

        assertTrue(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId).isEmpty());
        verifyNoInteractions(started, snapshots);
    }

    @Test void cancelledDeliveryIsNotReassigned() {
        UUID courierId = courier(), replacement = courier(), orderId = placedOrder();
        bootstrap.bootstrap(UUID.randomUUID(), "OrderAssigned", orderId, courierId);
        cancellation.cancel(UUID.randomUUID(), "OrderCancelled", orderId, Instant.now());

        reassignment.reassign(UUID.randomUUID(), orderId, courierId, replacement);

        assertEquals(courierId, deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId).orElseThrow().getCourierId());
        assertTrue(active(replacement).isEmpty());
    }

    @Test void cancellationAfterArrivalLeavesTheDeliveryUnchanged() {
        UUID courierId = courier(), orderId = placedOrder();
        bootstrap.bootstrap(UUID.randomUUID(), "OrderAssigned", orderId, courierId);
        var delivery = deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId).orElseThrow();
        delivery.arrive(Instant.now());
        deliveries.saveAndFlush(delivery);
        reset(snapshots);

        cancellation.cancel(UUID.randomUUID(), "OrderCancelled", orderId, Instant.now());

        assertNull(deliveries.findById(delivery.getId()).orElseThrow().getCancelledAt());
        verify(snapshots, never()).publish(any(UUID.class));
    }
}
