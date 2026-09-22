package com.fleetflow.tracking.domain;

/**
 * Domain Service (FR12) -- named explicitly in AGENTS.md's DDD pattern list.
 * Deliberately pure/static, same shape as EtaCalculationService, so both the distance
 * conversion and the threshold rules are unit-testable without any Kafka Streams
 * machinery. Reuses EtaCalculationService.haversineDistanceKm directly (package-visible)
 * rather than duplicating the Haversine formula.
 */

public final class GeofenceEvaluationService {

    /**
     * Locked defaults (requirements-outline.md: "pick defaults now, tune during
     * testing"). NEAR is loose enough to give a customer a few minutes' notice at
     * typical last-mile speeds; ARRIVAL is tight enough to tolerate GPS jitter without
     * firing early.
     */
    static final double NEAR_THRESHOLD_METERS = 500.0;
    static final double ARRIVAL_THRESHOLD_METERS = 50.0;

    private GeofenceEvaluationService() {

    }

    public static double distanceMeters(GeoCoordinate currentLocation, GeoCoordinate destination) {
        return EtaCalculationService.haversineDistanceKm(currentLocation, destination) * 1000.0;
    }

    public static boolean isNear(double distanceMeters) {
        return distanceMeters <= NEAR_THRESHOLD_METERS;
    }

    public static boolean isArrived(double distanceMeters) {
        return distanceMeters <= ARRIVAL_THRESHOLD_METERS;
    }
}
