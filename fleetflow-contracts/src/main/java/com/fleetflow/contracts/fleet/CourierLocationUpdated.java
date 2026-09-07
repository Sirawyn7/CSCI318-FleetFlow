package com.fleetflow.contracts.fleet;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.common.GeoPoint;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by Fleet on FR10, high-frequency. Consumers (Dispatch's
 * {@code courier_availability_view}, Tracking's ETA/geofence/anomaly topologies) must
 * store latest-position-per-courier only, never a ping history — that constraint is
 * locked design decision 2, not something this record enforces.
 */
public record CourierLocationUpdated(EventEnvelope envelope, UUID courierId, GeoPoint location, Instant capturedAt) {
}
