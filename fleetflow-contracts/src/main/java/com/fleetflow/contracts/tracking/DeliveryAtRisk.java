package com.fleetflow.contracts.tracking;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/**
 * Published by Tracking's anomaly detection topology (FR13). Carries {@code orderId}
 * and {@code courierId} directly, not just {@code deliveryId} — Dispatch is a documented
 * consumer (FR9 reassignment trigger) but does not consume {@code DeliveryStarted}, so it
 * has no existing translation table to resolve a bare {@code deliveryId} against.
 */
public record DeliveryAtRisk(EventEnvelope envelope, UUID deliveryId, UUID orderId, UUID courierId, String riskReason) {
}
