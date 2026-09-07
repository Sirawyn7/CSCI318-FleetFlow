package com.fleetflow.contracts.order;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.common.GeoPoint;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by Order on FR1. Carries only pickup/dropoff coordinates, not Order's
 * own richer {@code Address} value object — Order's infrastructure layer performs
 * that translation before publishing, which is the ACL boundary in action.
 */
public record OrderPlaced(
        EventEnvelope envelope,
        UUID orderId,
        UUID customerId,
        GeoPoint pickupLocation,
        GeoPoint dropoffLocation,
        String serviceLevel,
        Instant requestedDeliveryBy) {
}
