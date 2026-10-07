package com.fleetflow.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Notification-owned REST projections, not shared domain entities. */
public final class DeliveryData {
    private DeliveryData() { }
    public record Order(UUID id, UUID customerId, String status, Instant requestedDeliveryBy) { }
    public record Tracking(UUID orderId, Instant estimatedArrival, List<String> riskFlags, Instant updatedAt) {
        public Tracking { riskFlags = riskFlags == null ? null : List.copyOf(riskFlags); }
    }
}
