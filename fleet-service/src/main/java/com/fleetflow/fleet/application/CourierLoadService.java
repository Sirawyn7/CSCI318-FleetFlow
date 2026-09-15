package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class CourierLoadService {
    private final CourierJpaRepository couriers;
    private final CourierOrderLoadRepository loads;
    private final ProcessedEventRepository processed;

    public CourierLoadService(CourierJpaRepository couriers, CourierOrderLoadRepository loads,
                              ProcessedEventRepository processed) {
        this.couriers = couriers;
        this.loads = loads;
        this.processed = processed;
    }

    @Transactional
    public void apply(UUID eventId, UUID orderId, UUID courierId, boolean completed) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(courierId, "courierId");
        // Serialize changes to a courier, including profile edits and deactivation.
        Courier courier = couriers.findForUpdate(courierId)
                .orElseThrow(() -> new CourierNotFoundException(courierId));
        if (processed.existsById(eventId)) return;
        CourierOrderLoad load = loads.findById(orderId).orElse(null);
        if (load != null && !load.getCourierId().equals(courierId)) {
            throw new IllegalStateException("Order belongs to another courier; reassignment is outside this slice");
        }
        if (load == null) {
            if (!completed) courier.assignOrder();
            loads.save(new CourierOrderLoad(orderId, courierId, completed));
        } else if (completed && !load.isCompleted()) {
            courier.completeOrder();
            load.complete();
        }
        processed.save(new ProcessedEvent(eventId,
                completed ? "DeliveryCompleted" : "OrderAssigned", Instant.now()));
    }
}
