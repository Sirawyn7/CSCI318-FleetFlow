package com.fleetflow.dispatch.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root (FR7). Identity persists through change, so this is an Entity, not
 * a Value Object, per docs/AGENTS.md §9.3. Same shape as fleet-service's Courier:
 * invariants enforced in the {@link #create} factory so the aggregate can never be
 * constructed in an invalid state, regardless of caller. UC3 makes one decision
 * per order. Fleet acknowledgement releases the assignment's pending capacity
 * reservation without changing its courier or identity.
 */
@Entity
@Table(name = "assignments")
public class Assignment {

    @Id
    private UUID id;

    @jakarta.persistence.Column(unique = true, nullable = false)
    private UUID orderId;

    private UUID courierId;

    private Instant assignedAt;

    private boolean loadAcknowledged;
    @jakarta.persistence.Column(columnDefinition = "boolean default false", nullable = false)
    private boolean cancelled;
    @jakarta.persistence.Version
    @jakarta.persistence.Column(columnDefinition = "bigint default 0")
    private Long version;

    public boolean isCancelled() { return cancelled; }
    public void cancel() { cancelled = true; }

    public boolean isLoadAcknowledged() { return loadAcknowledged; }
    public void acknowledgeLoad() { loadAcknowledged = true; }

    protected Assignment() {
        // JPA
    }

    private Assignment(UUID id, UUID orderId, UUID courierId, Instant assignedAt) {
        this.id = id;
        this.orderId = orderId;
        this.courierId = courierId;
        this.assignedAt = assignedAt;
    }

    public static Assignment create(UUID orderId, UUID courierId, Instant assignedAt) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (courierId == null) {
            throw new IllegalArgumentException("courierId is required");
        }
        if (assignedAt == null) {
            throw new IllegalArgumentException("assignedAt is required");
        }
        return new Assignment(UUID.randomUUID(), orderId, courierId, assignedAt);
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

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
