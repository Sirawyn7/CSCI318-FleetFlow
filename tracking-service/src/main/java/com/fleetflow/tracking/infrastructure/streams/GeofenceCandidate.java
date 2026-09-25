package com.fleetflow.tracking.infrastructure.streams;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal to the geofence topologies -- the result of joining a location ping
 * against its delivery's snapshot, before each milestone processor decides whether
 * it's crossed its own threshold. Carries the ping's own capturedAt (not the time it
 * was processed), so DeliveryArrived records when the courier actually arrived, not
 * when this record happened to be processed.
 */

record GeofenceCandidate(UUID deliveryId, double distanceMeters, Instant capturedAt) {
}