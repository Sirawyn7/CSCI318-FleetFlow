package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class CourierLoadProjectionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CourierLoadProjectionService.class);
    private final CourierAvailabilityViewRepository couriers;
    private final AssignmentRepository assignments;
    private final ProcessedEventRepository processed;

    public CourierLoadProjectionService(CourierAvailabilityViewRepository couriers,
            AssignmentRepository assignments, ProcessedEventRepository processed) {
        this.couriers = couriers;
        this.assignments = assignments;
        this.processed = processed;
    }

    @Transactional
    public void apply(UUID eventId, String eventType, UUID courierId, int activeCount, int capacity, long version, UUID orderId) {
        if (processed.existsById(eventId)) return;
        // Assignment publication holds this same lock until commit/rollback. A fast
        // acknowledgement waits here before looking up its committed reservation.
        var courier = couriers.findForUpdate(courierId)
                .orElseThrow(() -> new IllegalStateException("Load received before courier registration"));
        if (processed.existsById(eventId)) return;
        courier.updateLoad(activeCount, capacity, version);
        // Older snapshots still acknowledge their own reservation, but cannot regress load.
        if (orderId != null) {
            var assignment = assignments.findByOrderId(orderId).orElse(null);
            if (assignment == null) {
                // Historical/seeded/rolled-back assignments need not exist in Dispatch.
                // Keep Fleet's authoritative load; there is no local reservation to release.
                log.warn("Applied load for courier {} without local assignment for order {}", courierId, orderId);
            } else if (!assignment.getCourierId().equals(courierId)) {
                if (!courierId.equals(assignment.getPreviousCourierId()))
                    throw new IllegalStateException("Load acknowledgement courier mismatch");
                assignment.acknowledgePreviousLoad();
            } else {
                assignment.acknowledgeLoad();
            }
        }
        processed.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
