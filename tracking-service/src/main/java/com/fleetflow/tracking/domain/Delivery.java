package com.fleetflow.tracking.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root (identity persists through its lifecycle: started -> tracked ->
 * completed, per AGENTS.md's DDD pattern list). Created once Dispatch assigns a
 * courier (see DeliveryBootstrapService) -- FR11's ETA topology reads the courier's
 * destination and average speed from a derived Kafka Streams state store, not this JPA
 * row directly, since that is hot-path state read on every location ping; this table
 * is the source-of-truth record of the delivery itself, not the operational read
 * model the streaming topology queries per ping (see AGENTS.md's source-of-truth vs
 * derived-state distinction, explicitly assessed).
 */

@Entity
@Table(name = "deliveries")
public class Delivery {
    @Id
    private UUID id;

    private UUID orderId;

    private UUID courierId;

    @Embedded
    private GeoCoordinate destination;

    private double averageSpeedKmh;

    private Instant startedAt;

    private Instant completedAt;
    private Instant courierAssignedAt;
    public Instant getMonitoringStartedAt() { return courierAssignedAt == null ? startedAt : courierAssignedAt; }

    /** Reassignment preserves delivery identity, history and the lifetime exception budget. */
    public void reassign(UUID previous, UUID replacement, double speed) {
        if (replacement == null || replacement.equals(previous) || !Double.isFinite(speed) || speed <= 0)
            throw new IllegalArgumentException("Valid replacement and speed required");
        if (courierId.equals(replacement)) return;
        if (!courierId.equals(previous) || completedAt != null || arrivedAt != null || cancelledAt != null)
            throw new IllegalStateException("Delivery cannot be reassigned");
        courierId = replacement;
        averageSpeedKmh = speed;
        courierAssignedAt = Instant.now();
    }
    private String routeSummary;
    private String confirmation;

    /** Completion proof is immutable audit evidence owned by this aggregate. */
    public boolean complete(UUID courierId, String routeSummary, String confirmation, Instant completedAt) {
        if (cancelledAt != null) {
            throw new IllegalStateException("A cancelled delivery cannot be completed");
        }
        if (!this.courierId.equals(courierId)) {
            throw new IllegalStateException("Only the assigned courier can complete this delivery");
        }
        String route = requiredText(routeSummary, "routeSummary");
        String proof = requiredText(confirmation, "confirmation");
        if (this.completedAt != null) {
            if (!this.routeSummary.equals(route) || !this.confirmation.equals(proof)) {
                throw new IllegalStateException("Completed delivery proof cannot be changed");
            }
            return false;
        }
        if (completedAt == null || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Completion cannot precede delivery start");
        }
        this.routeSummary = route;
        this.confirmation = proof;
        this.completedAt = completedAt;
        return true;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank() || value.strip().length() > 255) {
            throw new IllegalArgumentException(field + " must contain 1–255 characters");
        }
        return value.strip();
    }

    public Instant getCompletedAt() { return completedAt; }
    public String getRouteSummary() { return routeSummary; }
    public String getConfirmation() { return confirmation; }

    private Instant requestedDeliveryBy;

    private Instant arrivedAt;

    /** Arrival ends monitoring; proof of completion remains the separate FR14 concern. */
    public void arrive(Instant at) {
        if (at == null || at.isBefore(startedAt)) {
            throw new IllegalArgumentException("Arrival must be at or after delivery start");
        }
        if (arrivedAt == null && cancelledAt == null) arrivedAt = at;
    }

    public Instant getArrivedAt() { return arrivedAt; }

    private Instant cancelledAt;

    /**
     * FR3: a cancelled order stops being monitored. Arrival or completion already ended
     * the delivery, so a later cancellation leaves that record unchanged.
     *
     * @return whether the delivery changed
     */
    public boolean cancel(Instant at) {
        if (at == null) throw new IllegalArgumentException("Cancellation time is required");
        if (cancelledAt != null || arrivedAt != null || completedAt != null) return false;
        cancelledAt = at;
        return true;
    }

    public Instant getCancelledAt() { return cancelledAt; }

    protected Delivery() {
        // JPA
    }

    private Delivery(UUID id, UUID orderId, UUID courierId, GeoCoordinate destination,
                     double averageSpeedKmh, Instant startedAt) {
        this.id = id;
        this.orderId = orderId;
        this.courierId = courierId;
        this.destination = destination;
        this.averageSpeedKmh = averageSpeedKmh;
        this.startedAt = startedAt;
    }

    public static Delivery start(UUID orderId, UUID courierId, GeoCoordinate destination, double averageSpeedKmh) {
        return start(orderId, courierId, destination, averageSpeedKmh, null);
    }

    public static Delivery start(UUID orderId, UUID courierId, GeoCoordinate destination,
                                 double averageSpeedKmh, Instant requestedDeliveryBy) {
        if (orderId == null || courierId == null || destination == null) {
            throw new IllegalArgumentException("orderId, courierId and destination are required");
        }
        if (!Double.isFinite(averageSpeedKmh) || averageSpeedKmh <= 0) {
            throw new IllegalArgumentException("averageSpeedKmh must be positive");
        }
        Delivery delivery = new Delivery(UUID.randomUUID(), orderId, courierId, destination, averageSpeedKmh, Instant.now());
        delivery.requestedDeliveryBy = requestedDeliveryBy;
        return delivery;
    }

    public Instant getRequestedDeliveryBy() {
        return requestedDeliveryBy;
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

    public GeoCoordinate getDestination() {
        return destination;
    }

    public double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
