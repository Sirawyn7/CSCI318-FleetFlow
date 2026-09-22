package com.fleetflow.tracking.infrastructure.streams;

import java.util.UUID;

/**
 * Internal to the geofence topologies -- the result of joining a location ping
 * against its delivery's snapshot, before each milestone processor decides whether
 * it's crossed its own threshold.
 */
record GeofenceCandidate(UUID deliveryId, double distanceMeters) {
}
