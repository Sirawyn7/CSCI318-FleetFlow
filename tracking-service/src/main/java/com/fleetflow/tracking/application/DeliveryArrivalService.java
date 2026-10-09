package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Projects Tracking's own arrival event back into its delivery lifecycle. */
@Service
public class DeliveryArrivalService {
    private final DeliveryJpaRepository deliveries;
    private final CourierVehicleTypeViewRepository couriers;
    private final ActiveDeliverySnapshotPublisher publisher;

    public DeliveryArrivalService(DeliveryJpaRepository deliveries,
            CourierVehicleTypeViewRepository couriers, ActiveDeliverySnapshotPublisher publisher) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.publisher = publisher;
    }

    @Transactional
    public void arrive(UUID deliveryId, Instant arrivedAt) {
        var courierId = deliveries.findCourierIdByDeliveryId(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Missing delivery: " + deliveryId));
        // Share bootstrap/recovery's courier lock so their snapshots cannot resurrect arrivals.
        couriers.findForUpdate(courierId)
                .orElseThrow(() -> new IllegalStateException("Missing courier projection: " + courierId));
        // Load the entity only after locking, avoiding a stale first-level-cache copy.
        var delivery = deliveries.findById(deliveryId).orElseThrow();
        delivery.arrive(arrivedAt);
        deliveries.saveAndFlush(delivery);
        // Republish even on replay: a prior database/Kafka partial failure can be repaired.
        publisher.publish(delivery.getCourierId());
    }
}
