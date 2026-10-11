package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.infrastructure.messaging.CourierLoadChangedEventPublisher;
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
    private final CourierLoadChangedEventPublisher publisher;

    public CourierLoadService(CourierJpaRepository couriers, CourierOrderLoadRepository loads,
                              ProcessedEventRepository processed,
                              CourierLoadChangedEventPublisher publisher) {
        this.couriers = couriers;
        this.loads = loads;
        this.processed = processed;
        this.publisher = publisher;
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
        if (load != null && load.getCourierId() != null && !load.getCourierId().equals(courierId)) {
            throw new IllegalStateException("Order belongs to another courier; reassignment is outside this slice");
        }
        if (load == null) {
            if (!completed) courier.assignOrder();
            loads.save(new CourierOrderLoad(orderId, courierId, completed));
        } else if (load.getCourierId() == null) {
            // Cancellation arrived first: identify the courier for the acknowledgement,
            // but never restore this terminal order's load.
            load.identifyCourier(courierId);
        } else if (completed && !load.isCompleted()) {
            courier.completeOrder();
            load.complete();
        }
        processed.save(new ProcessedEvent(eventId,
                completed ? "DeliveryCompleted" : "OrderAssigned", Instant.now()));
        // Also acknowledge semantic duplicates and completion-before-assignment: a
        // Dispatch reservation must be released even when the counter did not change.
        loads.flush();
        publisher.publish(courier, orderId);
    }

    @Transactional
    public void cancel(UUID eventId, UUID orderId) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");
        if (processed.existsById(eventId)) return;
        CourierOrderLoad load = loads.findById(orderId).orElse(null);
        if (load == null) {
            // Retain a tombstone even when OrderAssigned has not reached Fleet yet.
            // The unique key and optimistic version make racing deliveries retry safely.
            loads.saveAndFlush(new CourierOrderLoad(orderId, null, true));
        } else if (load.getCourierId() != null) {
            Courier courier = couriers.findForUpdate(load.getCourierId())
                    .orElseThrow(() -> new CourierNotFoundException(load.getCourierId()));
            if (processed.existsById(eventId)) return;
            if (!load.isCompleted()) {
                courier.completeOrder();
                load.complete();
            }
            loads.flush();
            publisher.publish(courier, orderId);
        }
        processed.save(new ProcessedEvent(eventId, "OrderCancelled", Instant.now()));
    }
}
