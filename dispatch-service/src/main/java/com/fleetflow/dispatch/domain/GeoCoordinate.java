package com.fleetflow.dispatch.domain;

/**
 * Domain-owned coordinate value object, deliberately duplicated rather than reusing
 * contracts.common.GeoPoint (that record carries no behaviour, per ADR 0004 rule 1)
 * or fleet-service's own domain.GeoCoordinate (that one is @Embeddable and guards an
 * aggregate's own persisted invariant; this one is a transient calculation VO built
 * from data already validated at other services' boundaries, so re-validating here
 * would be redundant, not a missing invariant -- hence no range checks here unlike
 * fleet-service's version).
 */
public record GeoCoordinate(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Haversine great-circle distance -- the project's locked ETA-calculation method
     * (docs/AGENTS.md: "ETA uses Haversine distance x configured average speed per
     * vehicle type. No external routing/maps API."), reused here for FR7's proximity
     * ranking.
     */
    public double distanceKmTo(GeoCoordinate other) {
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
