package com.fleetflow.notification.infrastructure.persistence;

import jakarta.persistence.*;
import java.util.UUID;

/** Derived correlation only: cross-context references remain IDs. */
@Entity
@Table(name = "delivery_order_links")
public class DeliveryOrderLink {
    @Id private UUID deliveryId;
    private UUID orderId;
    protected DeliveryOrderLink() { }
    public DeliveryOrderLink(UUID deliveryId, UUID orderId) {
        this.deliveryId = deliveryId; this.orderId = orderId;
    }
    public UUID getOrderId() { return orderId; }
}
