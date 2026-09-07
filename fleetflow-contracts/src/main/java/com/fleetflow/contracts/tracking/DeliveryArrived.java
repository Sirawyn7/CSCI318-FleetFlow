package com.fleetflow.contracts.tracking;

import com.fleetflow.contracts.EventEnvelope;

import java.time.Instant;
import java.util.UUID;

/** Published by Tracking's geofence detection topology (FR12). */
public record DeliveryArrived(EventEnvelope envelope, UUID deliveryId, Instant arrivedAt) {
}
