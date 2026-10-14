package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.OrderCancellationState;
import com.fleetflow.tracking.infrastructure.persistence.OrderCancellationStateRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * FR3: OrderCancelled stops monitoring. Dropping the delivery from its courier's snapshot
 * stops the ETA, geofence and risk topologies, so no further delivery events (and no
 * notifications built from them) follow. If cancellation arrives before OrderAssigned,
 * the tombstone stops bootstrap from ever starting the delivery.
 */
@Service
public class DeliveryCancellationService {
    private final OrderCancellationStateRepository cancellations;
    private final DeliveryJpaRepository deliveries;
    private final CourierVehicleTypeViewRepository couriers;
    private final ActiveDeliverySnapshotPublisher snapshots;
    private final ProcessedEventRepository processed;

    public DeliveryCancellationService(OrderCancellationStateRepository cancellations, DeliveryJpaRepository deliveries,
            CourierVehicleTypeViewRepository couriers, ActiveDeliverySnapshotPublisher snapshots,
            ProcessedEventRepository processed) {
        this.cancellations = cancellations;
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.snapshots = snapshots;
        this.processed = processed;
    }

    @Transactional
    public void cancel(UUID eventId, String eventType, UUID orderId, Instant cancelledAt) {
        if (processed.existsById(eventId)) return;
        // Lock order: cancellation state, then courier, then delivery (as DeliveryBootstrapService).
        var state = cancellations.findForUpdate(orderId).orElseGet(() -> {
            var created = new OrderCancellationState(orderId);
            cancellations.saveAndFlush(created);
            return created;
        });
        state.cancel();

        var found = deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId);
        if (found.isPresent()) {
            UUID courierId = found.get().getCourierId();
            couriers.findForUpdate(courierId)
                    .orElseThrow(() -> new IllegalStateException("Missing courier projection: " + courierId));
            var delivery = deliveries.findForCompletion(found.get().getId()).orElseThrow();
            if (!courierId.equals(delivery.getCourierId()))
                throw new IllegalStateException("Delivery was reassigned while locking; retry cancellation");
            if (delivery.cancel(cancelledAt == null ? Instant.now() : cancelledAt)) {
                deliveries.saveAndFlush(delivery);
                snapshots.publish(courierId);
            }
        }
        processed.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
