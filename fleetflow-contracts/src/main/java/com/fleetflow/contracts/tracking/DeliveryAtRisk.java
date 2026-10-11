package com.fleetflow.contracts.tracking;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/**
 * Published by Tracking's anomaly detection topology (FR13). Carries {@code orderId}
 * and {@code courierId} directly, not just {@code deliveryId} — Dispatch is a documented
 * consumer (FR9 reassignment trigger). These IDs also let it reject stale risks from
 * the previous courier after a reassignment.
 */
public record DeliveryAtRisk(EventEnvelope envelope, UUID deliveryId, UUID orderId, UUID courierId, String riskReason) {
}
