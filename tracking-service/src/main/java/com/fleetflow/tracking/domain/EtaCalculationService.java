package com.fleetflow.tracking.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Domain Service (FR11) -- spans two inputs (a location and a destination) not
 * naturally owned by either alone, matching AGENTS.md's explicit pattern list.
 * Deliberately pure/static: no Kafka Streams or Spring dependency, so both the
 * Haversine calculation and the "material change" rule are unit-testable without any
 * streaming infrastructure. The Kafka Streams topology (infrastructure/streams/) is a
 * thin caller of this class, not where the business logic lives.
 */

public final class EtaCalculationService {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private EtaCalculationService() {
    }

    /**
     * Haversine great-circle distance divided by configured average speed per vehicle type
     * (locked decision, requirements-outline.md FR11). Deliberately no external
     * routing API -- see that decision's own documented rationale.
     */
    public static Instant estimateArrival(GeoCoordinate currentLocation, GeoCoordinate destination,
                                          double averageSpeedKmh, Instant now) {
        if (!Double.isFinite(averageSpeedKmh) || averageSpeedKmh <= 0) {
            throw new IllegalArgumentException("averageSpeedKmh must be finite and positive");
        }
        double distanceKm = haversineDistanceKm(currentLocation, destination);
        double hoursRemaining = distanceKm / averageSpeedKmh;
        long secondsRemaining = Math.round(hoursRemaining * 3600);
        return now.plusSeconds(secondsRemaining);
    }

    static double haversineDistanceKm(GeoCoordinate a, GeoCoordinate b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double deltaLat = Math.toRadians(b.latitude() - a.latitude());
        double deltaLon = Math.toRadians(b.longitude() - a.longitude());

        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        h = Math.max(0.0, Math.min(1.0, h));
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return EARTH_RADIUS_KM * c;
    }
    /**
     * FR11: "material change" republish rule. Kept here rather than inline in the
     * topology's processor so the rule itself -- not just the Haversine math -- is
     * unit-testable without any Kafka Streams state store machinery.
     */
    public static boolean isMaterialChange(Instant lastPublishedEstimate, Instant candidateEstimate,
                                           Duration threshold) {
        if (lastPublishedEstimate == null) {
            return true;
        }
        Duration delta = Duration.between(lastPublishedEstimate, candidateEstimate).abs();
        return delta.compareTo(threshold) > 0;
    }
}
