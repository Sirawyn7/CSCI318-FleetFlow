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
        UUID owner = loads.findCourierIdByOrderId(orderId).orElse(courierId);
        java.util.stream.Stream.of(owner, courierId).distinct().sorted().forEach(id -> couriers.findForUpdate(id)
                .orElseThrow(() -> new CourierNotFoundException(id)));
        // Serialize changes to a courier, including profile edits and deactivation.
        Courier courier = couriers.findForUpdate(courierId)
                .orElseThrow(() -> new CourierNotFoundException(courierId));
        if (processed.existsById(eventId)) return;
        CourierOrderLoad load = loads.findById(orderId).orElse(null);
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
        } else if (!load.isCompleted() && load.getCourierId().equals(previous)) {
            newCourier.assignOrder();
            oldCourier.completeOrder();
            load.reassign(replacement);
        } else if (!load.getCourierId().equals(previous) && !load.getCourierId().equals(replacement)) {
            throw new IllegalStateException("Unexpected assignment owner");
        }
        if (load.getCourierId().equals(replacement)) load.rememberPreviousCourier(previous);
        processed.save(new ProcessedEvent(eventId, "OrderReassigned", Instant.now()));
        publisher.publish(oldCourier, orderId);
        publisher.publish(newCourier, orderId);
