package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.persistence.*;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DeliveryReassignmentService {
    private final DeliveryJpaRepository deliveries;
    private final CourierVehicleTypeViewRepository couriers;
    private final ProcessedEventRepository processed;
    private final ActiveDeliverySnapshotPublisher snapshots;
    private final VehicleSpeedConfiguration speeds;
    public DeliveryReassignmentService(DeliveryJpaRepository deliveries, CourierVehicleTypeViewRepository couriers,
            ProcessedEventRepository processed, ActiveDeliverySnapshotPublisher snapshots, VehicleSpeedConfiguration speeds) {
        this.deliveries = deliveries; this.couriers = couriers; this.processed = processed;
        this.snapshots = snapshots; this.speeds = speeds;
    }

    @Transactional
    public void reassign(UUID eventId, UUID orderId, UUID previous, UUID replacement) {
        if (previous.equals(replacement)) throw new IllegalArgumentException("Replacement must differ");
        // Match the courier-before-delivery lock order used by completion and snapshot recovery.
        Stream.of(previous, replacement).sorted().forEach(id -> couriers.findForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Missing courier projection")));
        if (processed.existsById(eventId)) return;
        var found = deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("Delivery not started yet"));
        var delivery = deliveries.findForCompletion(found.getId()).orElseThrow();
        if (delivery.getCompletedAt() == null && delivery.getArrivedAt() == null && delivery.getCancelledAt() == null) {
            delivery.reassign(previous, replacement, speeds.kmhFor(couriers.findById(replacement).orElseThrow().getVehicleType()));
            deliveries.saveAndFlush(delivery);
        }
        snapshots.publish(previous);
        snapshots.publish(replacement);
        processed.save(new ProcessedEvent(eventId, "OrderReassigned", Instant.now()));
    }
}
