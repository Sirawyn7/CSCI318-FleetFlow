package com.fleetflow.order.domain;

import java.time.Instant;
import java.util.UUID;

public class Order {

    private final UUID id;
    private final UUID customerId;
    private final Address pickupAddress;
    private final Address dropoffAddress;
    private final String packageDetails;
    private final String serviceLevel;
    private final Instant requestedDeliveryBy;

    private OrderStatus status;
    private final Instant createdAt;

    public Order(
            UUID id,
            UUID customerId,
            Address pickupAddress,
            Address dropoffAddress,
            String packageDetails,
            String serviceLevel,
            Instant requestedDeliveryBy) {

        this.id = id;
        this.customerId = customerId;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
        this.packageDetails = packageDetails;
        this.serviceLevel = serviceLevel;
        this.requestedDeliveryBy = requestedDeliveryBy;
        this.status = OrderStatus.PLACED;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Address getPickupAddress() {
        return pickupAddress;
    }

    public Address getDropoffAddress() {
        return dropoffAddress;
    }

    public String getPackageDetails() {
        return packageDetails;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public Instant getRequestedDeliveryBy() {
        return requestedDeliveryBy;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Order(
        UUID id,
        UUID customerId,
        Address pickupAddress,
        Address dropoffAddress,
        String packageDetails,
        String serviceLevel,
        Instant requestedDeliveryBy,
        OrderStatus status,
        Instant createdAt) {

    this.id = id;
    this.customerId = customerId;
    this.pickupAddress = pickupAddress;
    this.dropoffAddress = dropoffAddress;
    this.packageDetails = packageDetails;
    this.serviceLevel = serviceLevel;
    this.requestedDeliveryBy = requestedDeliveryBy;
    this.status = status;
    this.createdAt = createdAt;
}
public void cancel() {
    if (status != OrderStatus.PLACED) {
        throw new IllegalStateException("Only placed orders can be cancelled.");
    }

    status = OrderStatus.CANCELLED;
}
}