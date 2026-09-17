package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.domain.CourierCandidate;
import com.fleetflow.dispatch.domain.DispatchAssignmentService;
import com.fleetflow.dispatch.domain.GeoCoordinate;
import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** UC3: agent first, deterministic fallback, one audited decision per order. */
@Service
public class AssignmentService {
    private final CourierAvailabilityViewRepository couriers;
    private final OpenOrderViewRepository orders;
    private final AssignmentRepository assignments;
    private final AssignmentDecisionRepository decisions;
    private final AssignmentEventPort events;
    private final BoundedDispatchAgent agent;
    private final OperatingZones zones;
    private final DispatchAssignmentService rules = new DispatchAssignmentService();

    public AssignmentService(CourierAvailabilityViewRepository couriers, OpenOrderViewRepository orders,
            AssignmentRepository assignments, AssignmentDecisionRepository decisions, AssignmentEventPort events,
            BoundedDispatchAgent agent, OperatingZones zones) {
        this.couriers = couriers;
        this.orders = orders;
        this.assignments = assignments;
        this.decisions = decisions;
        this.events = events;
        this.agent = agent;
        this.zones = zones;
    }

    @Transactional
    public void attemptAssignment(OpenOrderView incoming) {
        UUID orderId = incoming.getOrderId();
        if (decisions.findByOrderId(orderId).isPresent()) return;
        // A removed/cancelled order must not be assigned from an old event snapshot.
        OpenOrderView order = orders.findForUpdate(orderId).orElse(null);
        if (order == null || decisions.findByOrderId(orderId).isPresent()) return;

        // Lock in a stable order across instances. Reservations and Fleet acknowledgements
        // share these locks, so a burst of orders cannot reuse the same free slot.
        // The bounded agent receives copies only; it never touches this transaction.
        var pickup = new GeoCoordinate(order.getPickupLatitude(), order.getPickupLongitude());
        List<DispatchContext.CourierSnapshot> fleet = couriers.findAvailableForUpdate().stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .map(c -> snapshot(c, pickup)).toList();
        List<CourierCandidate> eligible = fleet.stream().filter(DispatchContext.CourierSnapshot::eligibleForTarget)
                .map(c -> new CourierCandidate(c.courierId(), c.remainingCapacity(), c.location())).toList();
        List<DispatchContext.OrderSnapshot> backlog = orders.findAll().stream().map(this::snapshot).toList();
        AgentAttempt attempt = agent.attempt(new DispatchContext(orderId, Instant.now(), backlog, fleet));
        AgentRecommendation proposal = attempt.recommendation();
        boolean valid = proposal != null && orderId.equals(proposal.orderId())
                && proposal.justification() != null && !proposal.justification().isBlank()
                && proposal.justification().length() <= 2000
                && eligible.stream().anyMatch(c -> c.courierId().equals(proposal.courierId()));

        UUID selected;
        AssignmentPath path;
        String reason;
        if (valid) {
            selected = proposal.courierId();
            path = AssignmentPath.AGENT;
            reason = proposal.justification();
        } else {
            selected = rules.select(pickup, eligible).map(CourierCandidate::courierId).orElse(null);
            path = AssignmentPath.RULE_BASED;
            String fallback = attempt.fallbackReason() == null ? "Agent recommendation rejected" : attempt.fallbackReason();
            reason = fallback + (selected == null
                    ? "; no available courier with known location, remaining capacity and a matching zone"
                    : "; selected nearest eligible courier, then greatest remaining capacity, then courier ID");
        }
        Instant decidedAt = Instant.now();
        decisions.save(new AssignmentDecision(orderId, selected, path,
                selected == null ? DecisionOutcome.FAILED : DecisionOutcome.ASSIGNED, reason, decidedAt));
        if (selected == null) {
            events.publishAssignmentFailed(orderId, reason);
            return;
        }
        Assignment assignment = Assignment.create(orderId, selected, decidedAt);
        assignments.save(assignment); // Also reserves one slot until Fleet acknowledges this order.
        orders.deleteById(orderId);
        events.publishOrderAssigned(assignment);
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
