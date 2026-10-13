package com.fleetflow.dispatch.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of an FR7/FR8 assignment attempt, not a domain aggregate -- same
 * reasoning §8 gives for processed_events ("infrastructure concern, not a domain
 * entity"), and AGENTS.md's §9.3 DDD table names only Assignment, not this, as an
 * Entity. Also this task's own idempotency ledger for the assignment concern: one
 * row per orderId, ever, checked before any selection attempt (see AssignmentService)
 * -- deliberately not the shared processed_events table, which is already claimed
 * for the same OrderPlaced event by OpenOrdersProjectionService.
 */
@Entity
@Table(name = "assignment_decisions")
public class AssignmentDecision {

    @Id
    private UUID id;

    @jakarta.persistence.Column(unique = true, nullable = false)
    private UUID orderId;

    private UUID courierId;

    @Enumerated(EnumType.STRING)
    private AssignmentPath pathUsed;

    @Enumerated(EnumType.STRING)
    private DecisionOutcome outcome;

    @jakarta.persistence.Column(length = 4000)
    private String reason;

    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    private AssignmentPath reassignmentPath;
    @Enumerated(EnumType.STRING)
    private DecisionOutcome reassignmentOutcome;
    @jakarta.persistence.Column(length = 4000)
    private String reassignmentReason;
    private Instant reassignmentDecidedAt;
    private UUID replacementCourierId;

    public void recordReassignment(UUID courierId, AssignmentPath path, DecisionOutcome outcome, String reason, Instant at) {
        this.replacementCourierId = courierId; this.reassignmentPath = path;
        this.reassignmentOutcome = outcome; this.reassignmentReason = reason; this.reassignmentDecidedAt = at;
    }
    public AssignmentPath getReassignmentPath() { return reassignmentPath; }
    public DecisionOutcome getReassignmentOutcome() { return reassignmentOutcome; }
    public String getReassignmentReason() { return reassignmentReason; }
    public Instant getReassignmentDecidedAt() { return reassignmentDecidedAt; }
    public UUID getReplacementCourierId() { return replacementCourierId; }

    protected AssignmentDecision() {
        // JPA
    }

    public AssignmentDecision(UUID orderId, UUID courierId, AssignmentPath pathUsed, DecisionOutcome outcome,
                               String reason, Instant decidedAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.courierId = courierId;
        this.pathUsed = pathUsed;
        this.outcome = outcome;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public AssignmentPath getPathUsed() {
        return pathUsed;
    }

    public DecisionOutcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
