package com.fleetflow.contracts.tracking;

import com.fleetflow.contracts.EventEnvelope;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by Tracking's ETA recalculation topology (FR11) on material change.
 * Carries only {@code deliveryId} — its one consumer, Notification, already has the
 * {@code deliveryId -> orderId} mapping from {@code DeliveryStarted}.
 */
public record DeliveryEtaUpdated(EventEnvelope envelope, UUID deliveryId, Instant estimatedArrival) {
}
