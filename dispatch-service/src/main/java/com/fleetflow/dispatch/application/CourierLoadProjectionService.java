package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class CourierLoadProjectionService {
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
    public void apply(UUID eventId, UUID courierId, int activeCount, int capacity, long version, UUID orderId) {
        var courier = couriers.findForUpdate(courierId)
                .orElseThrow(() -> new IllegalStateException("Load received before courier registration"));
        if (processed.existsById(eventId)) return;
        courier.updateLoad(activeCount, capacity, version);
        // Older snapshots still acknowledge their own reservation, but cannot regress load.
        if (orderId != null) {
            var assignment = assignments.findByOrderId(orderId)
                    .orElseThrow(() -> new IllegalStateException("Load received before assignment committed"));
            if (!assignment.getCourierId().equals(courierId)) {
                throw new IllegalStateException("Load acknowledgement courier mismatch");
            }
            assignment.acknowledgeLoad();
        }
        processed.save(new ProcessedEvent(eventId, "CourierLoadChanged", Instant.now()));
    }
}
