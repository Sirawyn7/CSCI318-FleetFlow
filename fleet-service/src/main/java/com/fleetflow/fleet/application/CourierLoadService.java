package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.infrastructure.messaging.CourierLoadChangedEventPublisher;
import com.fleetflow.fleet.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Objects;

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
        UUID owner = loads.findCourierIdByOrderId(orderId).orElse(courierId);
        java.util.stream.Stream.of(owner, courierId).distinct().sorted().forEach(id -> couriers.findForUpdate(id)
                .orElseThrow(() -> new CourierNotFoundException(id)));
        // Serialize changes to a courier, including profile edits and deactivation.
        Courier courier = couriers.findForUpdate(courierId)
                .orElseThrow(() -> new CourierNotFoundException(courierId));
        if (processed.existsById(eventId)) return;
        CourierOrderLoad load = loads.findById(orderId).orElse(null);
        if (load != null && load.getCourierId() != null && !load.getCourierId().equals(owner))
            throw new IllegalStateException("Load owner changed while locking; retry");
        if (load != null && load.getCourierId() != null && !load.getCourierId().equals(courierId)) {
            if (!completed && courierId.equals(load.getPreviousCourierId())) {
                processed.save(new ProcessedEvent(eventId, "OrderAssigned", Instant.now()));
                publisher.publish(courier, orderId);
                return;
            }
            if (completed && courierId.equals(load.getPreviousCourierId())) {
                var current = couriers.findById(owner).orElseThrow();
                if (!load.isCompleted()) { current.completeOrder(); load.complete(); }
                processed.save(new ProcessedEvent(eventId, "DeliveryCompleted", Instant.now()));
                publisher.publish(current, orderId);
                return;
            }
            throw new IllegalStateException("Order belongs to another courier");
        }
        if (load == null) {
            if (!completed) courier.assignOrder();
            loads.save(new CourierOrderLoad(orderId, courierId, completed));
        } else if (load.getCourierId() == null) {
            // A cancellation tombstone never regains load through late assignment events.
            load.identifyCourier(courierId);
        } else if (completed && !load.isCompleted()) {
            courier.completeOrder();
            load.complete();
        }
        processed.save(new ProcessedEvent(eventId,
                completed ? "DeliveryCompleted" : "OrderAssigned", Instant.now()));
        // Also acknowledge semantic duplicates and completion-before-assignment: a
        // Dispatch reservation must be released even when the counter did not change.
        publisher.publish(courier, orderId);
    }

    @Transactional
    public void reassign(UUID eventId, UUID orderId, UUID previous, UUID replacement) {
        if (previous.equals(replacement)) throw new IllegalArgumentException("Replacement must differ");
        // Sorted pair locking prevents transfers in opposite directions from deadlocking.
        java.util.stream.Stream.of(previous, replacement).sorted().forEach(id -> couriers.findForUpdate(id)
                .orElseThrow(() -> new CourierNotFoundException(id)));
        if (processed.existsById(eventId)) return;
        var oldCourier = couriers.findById(previous).orElseThrow();
        var newCourier = couriers.findById(replacement).orElseThrow();
        var load = loads.findById(orderId).orElse(null);
        if (load == null) {
            // Cross-topic delivery: reassignment can arrive before the initial assignment.
            load = new CourierOrderLoad(orderId, previous, false);
            load.reassign(replacement);
            newCourier.assignOrder();
            loads.save(load);
        } else if (load.getCourierId() == null) {
            // Cancellation arrived before either assignment event. Acknowledge both
            // couriers without acquiring load or discarding the terminal tombstone.
        } else if (!load.isCompleted() && load.getCourierId().equals(previous)) {
            newCourier.assignOrder();
            oldCourier.completeOrder();
            load.reassign(replacement);
        } else if (!load.getCourierId().equals(previous) && !load.getCourierId().equals(replacement)) {
            throw new IllegalStateException("Unexpected assignment owner");
        }
        if (replacement.equals(load.getCourierId())) load.rememberPreviousCourier(previous);
        processed.save(new ProcessedEvent(eventId, "OrderReassigned", Instant.now()));
        publisher.publish(oldCourier, orderId);
        publisher.publish(newCourier, orderId);
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
