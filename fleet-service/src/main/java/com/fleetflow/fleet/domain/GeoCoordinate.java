package com.fleetflow.fleet.domain;

import jakarta.persistence.Embeddable;

/**
 * Domain-owned coordinate value object -- deliberately distinct from
 * contracts.common.GeoPoint (see that record's Javadoc): this one enforces Fleet's own
 * validation invariant and is embedded directly on Courier, while GeoPoint is a
 * wire-only shape with no behavior, used only at the publisher boundary.
 */

@Embeddable
public record GeoCoordinate(double latitude, double longitude) {
    public GeoCoordinate {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        }
    }
}
