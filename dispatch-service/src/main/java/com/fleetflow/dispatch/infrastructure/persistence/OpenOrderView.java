package com.fleetflow.dispatch.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Derived read model (docs/decisions/0001-dispatch-open-orders-backlog.md): Dispatch's
 * own backlog of open orders for FR8's agent to reason over, kept current by consuming
 * Order's events. Not a domain aggregate and carries no invariants of its own — same
 * reasoning as {@link CourierAvailabilityView}, so this lives in
 * infrastructure/persistence/ rather than domain/. GeoPoint fields are flattened to
 * primitives rather than embedding the contracts record directly, since contract types
 * may not leak past infrastructure/ (docs/decisions/0004, rule 4).
 */
@Entity
@Table(name = "open_orders_view")
public class OpenOrderView {

    @Id
    private UUID orderId;

    private UUID customerId;

    private double pickupLatitude;

    private double pickupLongitude;

    private double dropoffLatitude;

    private double dropoffLongitude;

    private String serviceLevel;

    private Instant requestedDeliveryBy;

    private Instant placedAt;

    protected OpenOrderView() {
        // JPA
    }

    public OpenOrderView(UUID orderId, UUID customerId, double pickupLatitude, double pickupLongitude,
                          double dropoffLatitude, double dropoffLongitude, String serviceLevel,
                          Instant requestedDeliveryBy, Instant placedAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.dropoffLatitude = dropoffLatitude;
        this.dropoffLongitude = dropoffLongitude;
        this.serviceLevel = serviceLevel;
        this.requestedDeliveryBy = requestedDeliveryBy;
        this.placedAt = placedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public double getPickupLatitude() {
        return pickupLatitude;
    }

    public double getPickupLongitude() {
        return pickupLongitude;
    }

    public double getDropoffLatitude() {
        return dropoffLatitude;
    }

    public double getDropoffLongitude() {
        return dropoffLongitude;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public Instant getRequestedDeliveryBy() {
        return requestedDeliveryBy;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }
}
