package com.fleetflow.tracking.domain;
import java.time.Instant;
/** Immutable delivery-scoped observation, derived from Fleet GPS data. */
public record RiskObservation(Instant capturedAt, double latitude, double longitude, double distanceMeters) {
    public GeoCoordinate coordinate() { return new GeoCoordinate(latitude, longitude); }
}
