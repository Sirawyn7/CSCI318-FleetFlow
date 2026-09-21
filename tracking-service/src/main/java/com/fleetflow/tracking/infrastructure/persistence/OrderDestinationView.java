package com.fleetflow.tracking.infrastructure.persistence;

import com.fleetflow.tracking.domain.GeoCoordinate;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Derived read model (not source-of-truth -- Order owns that), projected from
 * OrderPlaced. Exists because OrderAssigned carries only orderId/courierId, not the
 * destination, so DeliveryBootstrapService needs somewhere to look the dropoff
 * coordinate up when an assignment arrives.
 */
@Entity
@Table(name = "order_destination_view")
public class OrderDestinationView {

    @Id
    private UUID orderId;

    @Embedded
    private GeoCoordinate dropoffLocation;

    private Instant requestedDeliveryBy;

    protected OrderDestinationView() {
        // JPA
    }

    public OrderDestinationView(UUID orderId, GeoCoordinate dropoffLocation, Instant requestedDeliveryBy) {
        this.orderId = orderId;
        this.dropoffLocation = dropoffLocation;
        this.requestedDeliveryBy = requestedDeliveryBy;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public GeoCoordinate getDropoffLocation() {
        return dropoffLocation;
    }

    public Instant getRequestedDeliveryBy() {
        return requestedDeliveryBy;
    }
}