package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rebuild internal snapshots on restart, including upgrades from the old one-delivery schema. */
@Service
public class ActiveDeliverySnapshotRecovery {
    private final DeliveryJpaRepository deliveries;
    private final CourierVehicleTypeViewRepository couriers;
    private final ActiveDeliverySnapshotPublisher publisher;
    public ActiveDeliverySnapshotRecovery(DeliveryJpaRepository deliveries,
            CourierVehicleTypeViewRepository couriers, ActiveDeliverySnapshotPublisher publisher) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.publisher = publisher;
    }
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recover() {
        deliveries.findAll().stream().map(d -> d.getCourierId()).distinct().sorted().forEach(id -> {
            couriers.findForUpdate(id).orElseThrow(() -> new IllegalStateException("Missing courier projection: " + id));
            var current = deliveries.findByCourierId(id);
            if (!current.isEmpty()) publisher.publish(current.getFirst());
        });
    }
}
