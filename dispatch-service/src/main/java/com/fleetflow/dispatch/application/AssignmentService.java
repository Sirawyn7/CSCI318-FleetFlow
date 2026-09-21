package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.CourierCandidate;
import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.domain.DispatchAssignmentService;
import com.fleetflow.dispatch.domain.GeoCoordinate;
import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final AssignmentEventPort events;
    private final TransactionTemplate commitTransaction;
    private final DispatchAssignmentService rules = new DispatchAssignmentService();

    public AssignmentService(CourierAvailabilityViewRepository couriers, OpenOrderViewRepository orders,
            AssignmentRepository assignments, AssignmentDecisionRepository decisions,
            BoundedDispatchAgent agent, OperatingZones zones, AssignmentEventPort events,
            PlatformTransactionManager transactionManager) {
        this.couriers = couriers;
        this.orders = orders;
        this.assignments = assignments;
        this.decisions = decisions;
        this.agent = agent;
        this.zones = zones;
        this.events = events;
        commitTransaction = new TransactionTemplate(transactionManager);
        commitTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
            if (tryAssign(orderId, proposal.courierId(), AssignmentPath.AGENT, proposal.justification())) return;
            fallback = "Agent recommendation became ineligible before commit";
        }

        // Re-read after the remote call. Each attempted reservation has its own short
        // transaction and locks only this order and one courier; failed attempts release both.
        List<CourierCandidate> eligible = fleetSnapshot(pickup).stream()
                .filter(DispatchContext.CourierSnapshot::eligibleForTarget)
                .map(c -> new CourierCandidate(c.courierId(), c.remainingCapacity(), c.location())).toList();
        String reason = fallback + "; selected nearest eligible courier, then greatest remaining capacity, then courier ID";
        for (CourierCandidate candidate : rules.rank(pickup, eligible)) {
            if (tryAssign(orderId, candidate.courierId(), AssignmentPath.RULE_BASED, reason)) return;
        }
        recordFailure(orderId, fallback
                + "; no available courier with known location, remaining capacity and a matching zone");
    }

    private List<DispatchContext.CourierSnapshot> fleetSnapshot(GeoCoordinate pickup) {
        return couriers.findByAvailableTrue().stream()
                .map(c -> snapshot(c, pickup))
                .filter(c -> c.location() != null).toList();
    }

    private DispatchContext.CourierSnapshot snapshot(CourierAvailabilityView courier, GeoCoordinate pickup) {
        int pending = assignments.countByCourierIdAndLoadAcknowledgedFalse(courier.getCourierId());
        return CourierEligibility.snapshot(courier, pickup, pending, zones);
    }

    /** Each candidate is revalidated under the same courier lock used by load acknowledgements. */
    private boolean tryAssign(UUID orderId, UUID courierId, AssignmentPath path, String reason) {
        return Boolean.TRUE.equals(commitTransaction.execute(status -> {
            var order = orders.findForUpdate(orderId).orElse(null);
            if (order == null || decisions.findByOrderId(orderId).isPresent()) return true;
            var courier = couriers.findForUpdate(courierId).orElse(null);
            var pickup = new GeoCoordinate(order.getPickupLatitude(), order.getPickupLongitude());
            if (courier == null || !snapshot(courier, pickup).eligibleForTarget()) return false;

            Instant decidedAt = Instant.now();
            var assignment = Assignment.create(orderId, courierId, decidedAt);
            assignments.save(assignment);
            decisions.save(new AssignmentDecision(orderId, courierId, path, DecisionOutcome.ASSIGNED, reason, decidedAt));
            orders.deleteById(orderId);
            events.publishOrderAssigned(assignment);
            return true;
        }));
    }

    private void recordFailure(UUID orderId, String reason) {
        commitTransaction.executeWithoutResult(status -> {
            if (orders.findForUpdate(orderId).isEmpty() || decisions.findByOrderId(orderId).isPresent()) return;
            decisions.save(new AssignmentDecision(orderId, null, AssignmentPath.RULE_BASED,
                    DecisionOutcome.FAILED, reason, Instant.now()));
            events.publishAssignmentFailed(orderId, reason);
        });
    }

    private DispatchContext.OrderSnapshot snapshot(OpenOrderView order) {
        return new DispatchContext.OrderSnapshot(order.getOrderId(),
                new GeoCoordinate(order.getPickupLatitude(), order.getPickupLongitude()),
                new GeoCoordinate(order.getDropoffLatitude(), order.getDropoffLongitude()),
                order.getServiceLevel(), order.getRequestedDeliveryBy(), order.getPlacedAt());
    }
}
