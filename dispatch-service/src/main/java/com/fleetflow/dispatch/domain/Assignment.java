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

    @jakarta.persistence.Version
    @jakarta.persistence.Column(columnDefinition = "bigint default 0")
    private long version;

    @jakarta.persistence.Column(unique = true, nullable = false)
    private UUID orderId;

    private UUID courierId;

    private Instant assignedAt;

    private boolean loadAcknowledged;

    private UUID deliveryId;
    @jakarta.persistence.Column(columnDefinition = "boolean default false")
    private boolean deliveryClosed;
    private Instant exceptionAttemptedAt;
    private Instant reassignedAt;
    private UUID previousCourierId;
    @jakarta.persistence.Column(columnDefinition = "boolean default false")
    private boolean previousLoadAcknowledged;
    public void acknowledgePreviousLoad() { previousLoadAcknowledged = true; }
    private UUID reassignmentEventId;
    @jakarta.persistence.Column(columnDefinition = "boolean default false")
    private boolean reassignmentPublished;
    private String exceptionReason;
    private String pendingException;
    private Instant escalatedAt;
    private String reassignmentPath;
    @jakarta.persistence.Column(length = 2200)
    private String reassignmentJustification;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Instant deliveryDeadline;
    private String serviceLevel;

    public void rememberDestination(double latitude, double longitude, Instant deadline, String serviceLevel) {
        new GeoCoordinate(latitude, longitude);
        this.destinationLatitude = latitude; this.destinationLongitude = longitude;
        this.deliveryDeadline = deadline; this.serviceLevel = serviceLevel;
    }
    public GeoCoordinate getDestination() {
        return destinationLatitude == null ? null : new GeoCoordinate(destinationLatitude, destinationLongitude);
    }
    public Instant getDeliveryDeadline() { return deliveryDeadline; }
    public String getServiceLevel() { return serviceLevel; }
    public String getReassignmentJustification() { return reassignmentJustification; }
    public void explainReassignment(String explanation) { reassignmentJustification = explanation; }

    public void startDelivery(UUID deliveryId) {
        if (this.deliveryId != null && !this.deliveryId.equals(deliveryId))
            throw new IllegalStateException("Order already has a delivery");
        this.deliveryId = java.util.Objects.requireNonNull(deliveryId);
    }

    public void closeDelivery() { deliveryClosed = true; pendingException = null; }
    public boolean isDeliveryActive() { return deliveryId != null && !deliveryClosed; }
    public UUID getDeliveryId() { return deliveryId; }
    public Instant getExceptionAttemptedAt() { return exceptionAttemptedAt; }
    public Instant getReassignedAt() { return reassignedAt; }
    public UUID getPreviousCourierId() { return previousCourierId; }
    public UUID getReassignmentEventId() { return reassignmentEventId; }
    public boolean isReassignmentPublished() { return reassignmentPublished; }
    public void markReassignmentPublished() { reassignmentPublished = true; }
    public String getExceptionReason() { return exceptionReason; }
    public String getPendingException() { return pendingException; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public String getReassignmentPath() { return reassignmentPath; }

    /** The aggregate owns the lifetime retry budget; changing couriers never resets it. */
    public void recordException(String reason, Instant now) {
        if (!isDeliveryActive() || escalatedAt != null) return;
        pendingException = java.util.Objects.requireNonNull(reason);
        escalateIfDue(now);
    }

    public void attemptException(Instant now) {
        if (!isDeliveryActive() || exceptionAttemptedAt != null)
            throw new IllegalStateException("Automated exception attempt already used or delivery inactive");
        exceptionAttemptedAt = java.util.Objects.requireNonNull(now);
    }

    public void reassign(UUID replacement, String reason, String path, Instant now) {
        if (exceptionAttemptedAt == null || reassignedAt != null || replacement == null
                || replacement.equals(courierId) || !isDeliveryActive())
            throw new IllegalStateException("Invalid reassignment");
        previousCourierId = courierId;
        previousLoadAcknowledged = loadAcknowledged;
        courierId = replacement;
        loadAcknowledged = false;
        reassignedAt = now;
        reassignmentEventId = UUID.randomUUID();
        exceptionReason = reason;
        reassignmentPath = path;
        pendingException = null;
    }

    public void escalateIfDue(Instant now) {
        if (isDeliveryActive() && pendingException != null && exceptionAttemptedAt != null
                && escalatedAt == null && !now.isBefore(exceptionAttemptedAt.plusSeconds(600)))
            escalatedAt = now;
    }

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
