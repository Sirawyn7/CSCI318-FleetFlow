package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.domain.GeoCoordinate;
import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Separate Spring bean so the short commit transaction is entered through its proxy. */
@Service
public class AssignmentCommitService {
    private final CourierAvailabilityViewRepository couriers;
    private final OpenOrderViewRepository orders;
    private final AssignmentRepository assignments;
    private final AssignmentDecisionRepository decisions;
    private final AssignmentEventPort events;
    private final OperatingZones zones;

    public AssignmentCommitService(CourierAvailabilityViewRepository couriers, OpenOrderViewRepository orders,
            AssignmentRepository assignments, AssignmentDecisionRepository decisions,
            AssignmentEventPort events, OperatingZones zones) {
        this.couriers = couriers;
        this.orders = orders;
        this.assignments = assignments;
        this.decisions = decisions;
        this.events = events;
        this.zones = zones;
    }

    /** True means completed or already handled; false asks the caller to try another candidate. */
    @Transactional
    public boolean tryAssign(UUID orderId, UUID courierId, AssignmentPath path, String reason) {
        var order = orders.findForUpdate(orderId).orElse(null);
        if (order == null || decisions.findByOrderId(orderId).isPresent()) return true;
        var courier = couriers.findForUpdate(courierId).orElse(null);
        if (courier == null || !courier.isAvailable() || courier.getLatitude() == null || courier.getLongitude() == null
                || !zones.serves(courier.getOperatingZone(), new GeoCoordinate(order.getPickupLatitude(), order.getPickupLongitude()))
                || courier.getCapacity() - courier.getActiveOrderCount()
                   - assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId) <= 0) return false;

        Instant decidedAt = Instant.now();
        var assignment = Assignment.create(orderId, courierId, decidedAt);
        assignments.save(assignment);
        decisions.save(new AssignmentDecision(orderId, courierId, path, DecisionOutcome.ASSIGNED, reason, decidedAt));
        orders.deleteById(orderId);
        events.publishOrderAssigned(assignment);
        return true;
    }

    @Transactional
    public void recordFailure(UUID orderId, String reason) {
        if (orders.findForUpdate(orderId).isEmpty() || decisions.findByOrderId(orderId).isPresent()) return;
        decisions.save(new AssignmentDecision(orderId, null, AssignmentPath.RULE_BASED,
                DecisionOutcome.FAILED, reason, Instant.now()));
        events.publishAssignmentFailed(orderId, reason);
    }
}
