package com.fleetflow.contracts.tracking;

import com.fleetflow.contracts.EventEnvelope;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by Tracking on FR14. Carries {@code orderId} and {@code courierId} directly,
 * not just {@code deliveryId} — Fleet is an inferred consumer (FR6 active-order-count
 * decrement) but does not consume {@code DeliveryStarted}, so it has no existing
 * translation table to resolve a bare {@code deliveryId} against.
 */
public record DeliveryCompleted(EventEnvelope envelope, UUID deliveryId, UUID orderId, UUID courierId, Instant completedAt) {
}
