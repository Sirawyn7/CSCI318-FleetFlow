package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryCompletedEventPublisher;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DeliveryCompletionService {
    private final DeliveryJpaRepository repository;
    private final DeliveryCompletedEventPublisher publisher;
    private final ActiveDeliverySnapshotPublisher snapshots;
    private final CourierVehicleTypeViewRepository couriers;

    public DeliveryCompletionService(DeliveryJpaRepository repository,
            DeliveryCompletedEventPublisher publisher, ActiveDeliverySnapshotPublisher snapshots,
            CourierVehicleTypeViewRepository couriers) {
        this.couriers = couriers;
        this.repository = repository;
        this.publisher = publisher;
        this.snapshots = snapshots;
    }

    @Transactional
    public Delivery complete(UUID id, UUID courierId, String routeSummary, String confirmation) {
        if (courierId == null) throw new IllegalArgumentException("courierId is required");
        UUID assignedCourier = repository.findCourierIdByDeliveryId(id)
                .orElseThrow(() -> new NoSuchElementException("Delivery not found"));
        // Use the same lock order as arrival, bootstrap and recovery to prevent stale snapshots.
        couriers.findForUpdate(assignedCourier)
                .orElseThrow(() -> new IllegalStateException("Missing courier projection: " + assignedCourier));
        Delivery delivery = repository.findForCompletion(id)
                .orElseThrow(() -> new NoSuchElementException("Delivery not found"));
        if (!assignedCourier.equals(delivery.getCourierId()))
            throw new IllegalStateException("Delivery was reassigned while locking; retry with current courier");
        if (delivery.complete(courierId, routeSummary, confirmation, Instant.now())) {
            repository.saveAndFlush(delivery);
            // A snapshot failure must occur before the external completion event is emitted.
            snapshots.publish(delivery);
            publisher.publish(delivery);
        }
        return delivery;
    }

    @Transactional(readOnly = true)
    public Delivery get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Delivery not found"));
    }

    @Transactional(readOnly = true)
    public List<Delivery> history(UUID orderId) {
        return repository.findByOrderIdOrderByStartedAtAsc(orderId);
    }
}
