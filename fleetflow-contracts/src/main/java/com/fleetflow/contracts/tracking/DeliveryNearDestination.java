package com.fleetflow.contracts.tracking;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/** Published by Tracking's geofence detection topology (FR12). */
public record DeliveryNearDestination(EventEnvelope envelope, UUID deliveryId, double distanceMetersRemaining) {
}
