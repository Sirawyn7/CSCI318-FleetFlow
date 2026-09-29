package com.fleetflow.tracking.domain;

import jakarta.persistence.Embeddable;

/**
 * Domain-owned coordinate value object. Deliberately Tracking's own copy, not shared
 * with fleet-service's GeoCoordinate or contracts.common.GeoPoint -- each bounded
 * context owns its own value objects (docs/decisions/0004); the only shared shape is
 * the wire-level GeoPoint used in event payloads.
 */

@Embeddable
public record GeoCoordinate(double latitude, double longitude) {
    public GeoCoordinate {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }
}
