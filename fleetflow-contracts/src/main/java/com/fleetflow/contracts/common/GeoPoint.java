package com.fleetflow.contracts.common;

/**
 * Wire-only coordinate pair. Distinct from every service's own {@code GeoCoordinate}
 * value object — those carry context-specific lifecycle (capture time, staleness,
 * series membership) that this type deliberately does not.
 */
public record GeoPoint(double latitude, double longitude) {
}
