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

    private Instant requestedDeliveryBy;

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
