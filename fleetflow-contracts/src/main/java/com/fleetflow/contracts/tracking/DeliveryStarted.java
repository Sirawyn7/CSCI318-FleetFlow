package com.fleetflow.contracts.tracking;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/**
 * Published by Tracking after consuming {@code OrderAssigned}. Carries {@code orderId}
 * and {@code courierId} directly so its consumers (Order, Notification) can build their
 * own {@code deliveryId -> orderId/courierId} translation without a lookup back to Tracking.
 */
public record DeliveryStarted(EventEnvelope envelope, UUID deliveryId, UUID orderId, UUID courierId) {
}
