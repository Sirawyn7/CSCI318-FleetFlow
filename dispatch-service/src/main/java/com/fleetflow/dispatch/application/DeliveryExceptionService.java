package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.*;
import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

/** UC4: agent calls use detached snapshots; the lifetime guard is checked under an assignment lock. */
@Service
public class DeliveryExceptionService {
    private final AssignmentRepository assignments;
    private final CourierAvailabilityViewRepository couriers;
    private final BoundedDispatchAgent agent;
    private final OperatingZones zones;
    private final AssignmentEventPort events;
    private final AssignmentDecisionRepository decisions;
    private final TransactionTemplate transaction;

    public DeliveryExceptionService(AssignmentRepository assignments, CourierAvailabilityViewRepository couriers,
            BoundedDispatchAgent agent, OperatingZones zones, AssignmentEventPort events,
            PlatformTransactionManager manager, AssignmentDecisionRepository decisions) {
        this.assignments = assignments; this.couriers = couriers; this.agent = agent;
        this.zones = zones; this.events = events; this.transaction = new TransactionTemplate(manager);
        this.decisions = decisions;
    }

    public void started(UUID orderId, UUID deliveryId, UUID courierId) {
        transaction.executeWithoutResult(status -> {
            var a = required(orderId);
            if (!a.getCourierId().equals(courierId) && !courierId.equals(a.getPreviousCourierId()))
                throw new IllegalStateException("Delivery courier does not match assignment");
            a.startDelivery(deliveryId);
        });
        checkOffline(orderId);
    }

    public void closed(UUID orderId, UUID deliveryId) {
        transaction.executeWithoutResult(status -> {
            var a = required(orderId);
            a.startDelivery(deliveryId); // closure before DeliveryStarted remains terminal
            a.closeDelivery();
        });
    }

    public void arrived(UUID deliveryId) {
        var a = assignments.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new IllegalStateException("DeliveryStarted not projected yet"));
        closed(a.getOrderId(), deliveryId);
    }

    public void risk(UUID orderId, UUID deliveryId, UUID courierId, String reason, Instant occurredAt) {
        if (!"SLA_BREACH".equals(reason)) return;
        handle(orderId, deliveryId, courierId, reason, occurredAt);
    }

    public void checkOffline(UUID orderId) {
        var a = assignments.findByOrderId(orderId).orElse(null);
        if (a == null || !a.isDeliveryActive()) return;
        var c = couriers.findById(a.getCourierId()).orElse(null);
        if (c != null && !c.isAvailable())
            handle(orderId, a.getDeliveryId(), a.getCourierId(), "COURIER_OFFLINE", Instant.now());
    }

    public void offline(UUID courierId) {
        assignments.findByDeliveryIdIsNotNullAndDeliveryClosedFalse().stream()
                .filter(a -> a.getCourierId().equals(courierId)).forEach(a -> checkOffline(a.getOrderId()));
    }

    private void handle(UUID orderId, UUID deliveryId, UUID courierId, String reason, Instant occurredAt) {
        var a = assignments.findByOrderId(orderId).orElseThrow(() -> new IllegalStateException("Missing assignment"));
        if (!a.isDeliveryActive()) {
            if (a.getDeliveryId() == null) throw new IllegalStateException("DeliveryStarted not projected yet");
            return;
        }
        if (!deliveryId.equals(a.getDeliveryId()) || !courierId.equals(a.getCourierId())
                || occurredAt.isBefore(a.getReassignedAt() == null ? a.getAssignedAt() : a.getReassignedAt())) return;
        if (a.getExceptionAttemptedAt() != null) {
            transaction.executeWithoutResult(s -> {
                var locked = required(orderId);
                if (locked.getCourierId().equals(courierId)) locked.recordException(reason, Instant.now());
            });
            return;
        }

        var current = couriers.findById(courierId).orElseThrow();
        GeoCoordinate handoff = current.getLatitude() == null || current.getLongitude() == null ? null
                : new GeoCoordinate(current.getLatitude(), current.getLongitude());
        var fleet = handoff == null ? List.<DispatchContext.CourierSnapshot>of() : couriers.findByAvailableTrue().stream()
                .filter(c -> !c.getCourierId().equals(courierId))
                .map(c -> snapshot(c, handoff)).filter(c -> c.location() != null).toList();
        AgentAttempt attempt = handoff == null || a.getDestination() == null
                ? new AgentAttempt(null, "Missing handoff location or historical destination")
                : agent.attempt(new DispatchContext(orderId, Instant.now(), List.of(new DispatchContext.OrderSnapshot(
                        orderId, handoff, a.getDestination(), a.getServiceLevel(), a.getDeliveryDeadline(), a.getAssignedAt())), fleet));
        var proposal = attempt.recommendation();
        boolean valid = proposal != null && orderId.equals(proposal.orderId()) && proposal.justification() != null
                && !proposal.justification().isBlank() && proposal.justification().length() <= 2000
                && fleet.stream().anyMatch(c -> c.eligibleForTarget() && c.courierId().equals(proposal.courierId()));
        List<UUID> ranked = new ArrayList<>();
        if (valid) ranked.add(proposal.courierId());
        if (handoff != null) new DispatchAssignmentService().rank(handoff, fleet.stream()
                .filter(DispatchContext.CourierSnapshot::eligibleForTarget)
                .map(c -> new CourierCandidate(c.courierId(), c.remainingCapacity(), c.location())).toList())
                .forEach(c -> { if (!ranked.contains(c.courierId())) ranked.add(c.courierId()); });
        transaction.executeWithoutResult(status -> {
            var locked = required(orderId);
            if (!locked.isDeliveryActive() || !locked.getCourierId().equals(courierId)) return;
            // Concurrent triggers share the same one-attempt budget, including the no-candidate case.
            if (locked.getExceptionAttemptedAt() != null) return;
            Instant now = Instant.now();
            locked.recordException(reason, now);
            locked.attemptException(now);
            for (UUID candidateId : ranked) {
                var candidate = couriers.findForUpdate(candidateId).orElse(null);
                if (candidate == null || !snapshot(candidate, handoff).eligibleForTarget()) continue;
                locked.reassign(candidateId, reason,
                        valid && candidateId.equals(proposal.courierId()) ? "AGENT" : "RULE_BASED", now);
                locked.explainReassignment(valid && candidateId.equals(proposal.courierId()) ? proposal.justification()
                        : (attempt.fallbackReason() == null ? "Agent recommendation rejected or became ineligible" : attempt.fallbackReason())
                        + "; nearest eligible courier to handoff, then capacity and courier ID");
                decisions.findByOrderId(orderId).ifPresent(d -> d.recordReassignment(candidateId,
                        AssignmentPath.valueOf(locked.getReassignmentPath()), DecisionOutcome.ASSIGNED,
                        locked.getReassignmentJustification(), now));
                return;
            }
            locked.recordException(reason + ": NO_ELIGIBLE_COURIER", now);
            decisions.findByOrderId(orderId).ifPresent(d -> d.recordReassignment(null, AssignmentPath.RULE_BASED,
                    DecisionOutcome.FAILED, reason + ": NO_ELIGIBLE_COURIER", now));
        });
    }

    private DispatchContext.CourierSnapshot snapshot(CourierAvailabilityView c, GeoCoordinate handoff) {
        return CourierEligibility.snapshot(c, handoff,
                assignments.countByCourierIdAndLoadAcknowledgedFalse(c.getCourierId()), zones);
    }

    private Assignment required(UUID orderId) {
        return assignments.findForUpdate(orderId).orElseThrow(() -> new IllegalStateException("Missing assignment"));
    }

    public void publishPending(UUID orderId) {
        transaction.executeWithoutResult(status -> {
            var a = required(orderId);
            if (a.getReassignedAt() == null || a.isReassignmentPublished()) return;
            // The assignment is a single-entry transactional outbox: stable event ID on every retry.
            events.publishOrderReassigned(a);
            a.markReassignmentPublished();
        });
    }

    public void escalateDue(UUID orderId, Instant now) {
        transaction.executeWithoutResult(status -> required(orderId).escalateIfDue(now));
    }

    public List<OperatorException> operatorQueue() {
        return assignments.findByEscalatedAtIsNotNullAndDeliveryClosedFalse().stream()
                .map(a -> new OperatorException(a.getOrderId(), a.getDeliveryId(), a.getCourierId(),
                        a.getPendingException(), a.getExceptionAttemptedAt(), a.getEscalatedAt())).toList();
    }

    public record OperatorException(UUID orderId, UUID deliveryId, UUID courierId, String reason,
                                    Instant attemptedAt, Instant escalatedAt) {}
}
