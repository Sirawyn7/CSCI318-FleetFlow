package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.CourierCandidate;
import com.fleetflow.dispatch.domain.DispatchAssignmentService;
import com.fleetflow.dispatch.domain.GeoCoordinate;
import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** UC3 orchestration: the external agent never runs inside a database transaction. */
@Service
public class AssignmentService {
    private final CourierAvailabilityViewRepository couriers;
    private final OpenOrderViewRepository orders;
    private final AssignmentRepository assignments;
    private final AssignmentDecisionRepository decisions;
    private final BoundedDispatchAgent agent;
    private final OperatingZones zones;
    private final AssignmentCommitService committer;
    private final DispatchAssignmentService rules = new DispatchAssignmentService();

    public AssignmentService(CourierAvailabilityViewRepository couriers, OpenOrderViewRepository orders,
            AssignmentRepository assignments, AssignmentDecisionRepository decisions,
            BoundedDispatchAgent agent, OperatingZones zones, AssignmentCommitService committer) {
        this.couriers = couriers;
        this.orders = orders;
        this.assignments = assignments;
        this.decisions = decisions;
        this.agent = agent;
        this.zones = zones;
        this.committer = committer;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void attemptAssignment(OpenOrderView incoming) {
        UUID orderId = incoming.getOrderId();
        if (decisions.findByOrderId(orderId).isPresent()) return;
        OpenOrderView order = orders.findById(orderId).orElse(null);
        if (order == null) return;
        var pickup = new GeoCoordinate(order.getPickupLatitude(), order.getPickupLongitude());
        var fleet = fleetSnapshot(pickup);
        var backlog = orders.findAll().stream().map(this::snapshot).toList();
        AgentAttempt attempt = agent.attempt(new DispatchContext(orderId, Instant.now(), backlog, fleet));
        AgentRecommendation proposal = attempt.recommendation();
        boolean valid = proposal != null && orderId.equals(proposal.orderId())
                && proposal.justification() != null && !proposal.justification().isBlank()
                && proposal.justification().length() <= 2000
                && fleet.stream().anyMatch(c -> c.eligibleForTarget() && c.courierId().equals(proposal.courierId()));
        String fallback = attempt.fallbackReason() == null ? "Agent recommendation rejected" : attempt.fallbackReason();
        if (valid) {
            if (committer.tryAssign(orderId, proposal.courierId(), AssignmentPath.AGENT, proposal.justification())) return;
            fallback = "Agent recommendation became ineligible before commit";
        }

        // Re-read after the remote call. Each attempted reservation has its own short
        // transaction and locks only this order and one courier; failed attempts release both.
        List<CourierCandidate> eligible = fleetSnapshot(pickup).stream()
                .filter(DispatchContext.CourierSnapshot::eligibleForTarget)
                .map(c -> new CourierCandidate(c.courierId(), c.remainingCapacity(), c.location())).toList();
        String reason = fallback + "; selected nearest eligible courier, then greatest remaining capacity, then courier ID";
        for (CourierCandidate candidate : rules.rank(pickup, eligible)) {
            if (committer.tryAssign(orderId, candidate.courierId(), AssignmentPath.RULE_BASED, reason)) return;
        }
        committer.recordFailure(orderId, fallback
                + "; no available courier with known location, remaining capacity and a matching zone");
    }

    private List<DispatchContext.CourierSnapshot> fleetSnapshot(GeoCoordinate pickup) {
        return couriers.findByAvailableTrue().stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .map(c -> snapshot(c, pickup)).toList();
    }

    private DispatchContext.CourierSnapshot snapshot(CourierAvailabilityView courier, GeoCoordinate pickup) {
        int pending = assignments.countByCourierIdAndLoadAcknowledgedFalse(courier.getCourierId());
        int remaining = Math.max(0, courier.getCapacity() - courier.getActiveOrderCount() - pending);
        return new DispatchContext.CourierSnapshot(courier.getCourierId(), courier.getVehicleType(),
                courier.getOperatingZone(), new GeoCoordinate(courier.getLatitude(), courier.getLongitude()),
                courier.getCapacity(), courier.getActiveOrderCount(), pending, remaining,
                remaining > 0 && zones.serves(courier.getOperatingZone(), pickup));
    }

    private DispatchContext.OrderSnapshot snapshot(OpenOrderView order) {
        return new DispatchContext.OrderSnapshot(order.getOrderId(),
                new GeoCoordinate(order.getPickupLatitude(), order.getPickupLongitude()),
                new GeoCoordinate(order.getDropoffLatitude(), order.getDropoffLongitude()),
                order.getServiceLevel(), order.getRequestedDeliveryBy(), order.getPlacedAt());
    }
}
