package com.fleetflow.order.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Aggregate root; field-based JPA mapping follows the Fleet reference slice. */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;
    private UUID customerId;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "pickup_street")),
        @AttributeOverride(name = "suburb", column = @Column(name = "pickup_suburb")),
        @AttributeOverride(name = "state", column = @Column(name = "pickup_state")),
        @AttributeOverride(name = "postcode", column = @Column(name = "pickup_postcode")),
        @AttributeOverride(name = "latitude", column = @Column(name = "pickup_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "pickup_longitude"))
    })
    private Address pickupAddress;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "dropoff_street")),
        @AttributeOverride(name = "suburb", column = @Column(name = "dropoff_suburb")),
        @AttributeOverride(name = "state", column = @Column(name = "dropoff_state")),
        @AttributeOverride(name = "postcode", column = @Column(name = "dropoff_postcode")),
        @AttributeOverride(name = "latitude", column = @Column(name = "dropoff_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "dropoff_longitude"))
    })
    private Address dropoffAddress;
    private String packageDetails;
    private String serviceLevel;
    private Instant requestedDeliveryBy;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "order_status_history", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "sequence_number")
    private List<OrderStatusChange> statusHistory = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "item_position")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() { }

    public List<OrderItem> getItems() { return List.copyOf(items); }

    public Order(UUID id, UUID customerId, Address pickupAddress, Address dropoffAddress,
                 String packageDetails, String serviceLevel, Instant requestedDeliveryBy,
                 List<OrderItem> items) {
        this(id, customerId, pickupAddress, dropoffAddress, packageDetails, serviceLevel, requestedDeliveryBy);
        if (items == null || items.isEmpty() || items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("At least one valid order item is required");
        }
        this.items = new ArrayList<>(items);
    }

    public List<OrderStatusChange> getStatusHistory() { return List.copyOf(statusHistory); }

    public Order(
            UUID id,
            UUID customerId,
            Address pickupAddress,
            Address dropoffAddress,
            String packageDetails,
            String serviceLevel,
            Instant requestedDeliveryBy) {

        if (id == null || customerId == null || pickupAddress == null || dropoffAddress == null
                || requestedDeliveryBy == null) {
            throw new IllegalArgumentException("Order identity, addresses and delivery deadline are required");
        }
        if (packageDetails == null || packageDetails.isBlank() || serviceLevel == null || serviceLevel.isBlank()) {
            throw new IllegalArgumentException("Package details and service level are required");
        }
        if (packageDetails.length() > 255 || serviceLevel.length() > 255) {
            throw new IllegalArgumentException("Package details and service level must not exceed 255 characters");
        }
        this.id = id;
        this.customerId = customerId;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
        this.packageDetails = packageDetails;
        this.serviceLevel = serviceLevel;
        this.requestedDeliveryBy = requestedDeliveryBy;
        this.status = OrderStatus.PLACED;
        this.createdAt = Instant.now();
        statusHistory.add(new OrderStatusChange(status, createdAt));
        items.add(new OrderItem(packageDetails, 1));
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

    public void cancel() {
        if (status != OrderStatus.PLACED) {
            throw new OrderCancellationException();
        }
        status = OrderStatus.CANCELLED;
        statusHistory.add(new OrderStatusChange(status, Instant.now()));
    }

    /**
     * Applies a lifecycle transition reported by Dispatch or Tracking (FR2). Transitions
     * only move forward, so duplicate, late or out-of-order events are no-ops; this makes
     * the consumers idempotent without a processed-event ledger. Terminal orders never change.
     *
     * @return whether the status changed
     */
    public boolean advanceTo(OrderStatus target, Instant at) {
        if (target == null || at == null) {
            throw new IllegalArgumentException("Target status and transition time are required");
        }
        if (target == OrderStatus.PLACED || target == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException(target + " is not reported by other services");
        }
        if (isTerminal(status)) return false;
        if (target == OrderStatus.FAILED ? status != OrderStatus.PLACED : progress(target) <= progress(status)) {
            return false;
        }
        status = target;
        statusHistory.add(new OrderStatusChange(target, at));
        return true;
    }

    private static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.DELIVERED || status == OrderStatus.FAILED || status == OrderStatus.CANCELLED;
    }

    private static int progress(OrderStatus status) {
        return switch (status) {
            case PLACED -> 0;
            case ASSIGNED -> 1;
            case IN_TRANSIT -> 2;
            case DELIVERED -> 3;
            case FAILED, CANCELLED -> throw new IllegalStateException(status + " has no progress rank");
        };
    }
}
