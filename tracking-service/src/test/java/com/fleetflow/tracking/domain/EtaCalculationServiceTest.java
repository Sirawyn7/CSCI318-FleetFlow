package com.fleetflow.tracking.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaCalculationServiceTest {

    @Test
    void haversineDistanceOfIdenticalPointsIsZero() {
        GeoCoordinate point = new GeoCoordinate(-33.8688, 151.2093);

        assertEquals(0.0, EtaCalculationService.haversineDistanceKm(point, point), 0.0001);
    }

    @Test
    void haversineDistanceMatchesKnownSydneyToMelbourneDistance() {
        GeoCoordinate sydney = new GeoCoordinate(-33.8688, 151.2093);
        GeoCoordinate melbourne = new GeoCoordinate(-37.8136, 144.9631);

        double distanceKm = EtaCalculationService.haversineDistanceKm(sydney, melbourne);

        assertTrue(distanceKm > 700 && distanceKm < 730, "expected ~713km, was " + distanceKm);
    }

    @Test
    void estimateArrivalAddsDistanceOverSpeedToNow() {
        GeoCoordinate origin = new GeoCoordinate(0, 0);
        GeoCoordinate destination = new GeoCoordinate(0, 1);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        Instant eta = EtaCalculationService.estimateArrival(origin, destination, 111.0, now);

        long minutesAhead = Duration.between(now, eta).toMinutes();
        assertTrue(minutesAhead >= 55 && minutesAhead <= 65, "expected ~60 minutes, was " + minutesAhead);
    }

    @Test
    void estimateArrivalRejectsNonPositiveSpeed() {
        GeoCoordinate point = new GeoCoordinate(0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> EtaCalculationService.estimateArrival(point, point, 0, Instant.now()));
    }

    @Test
    void isMaterialChangeIsTrueWhenNoPriorEstimateExists() {
        assertTrue(EtaCalculationService.isMaterialChange(null, Instant.now(), Duration.ofSeconds(60)));
    }

    @Test
    void isMaterialChangeIsFalseWithinThreshold() {
        Instant last = Instant.parse("2026-01-01T00:00:00Z");
        Instant candidate = last.plusSeconds(30);

        assertFalse(EtaCalculationService.isMaterialChange(last, candidate, Duration.ofSeconds(60)));
    }

    @Test
    void isMaterialChangeIsTrueBeyondThreshold() {
        Instant last = Instant.parse("2026-01-01T00:00:00Z");
        Instant candidate = last.plusSeconds(90);

        assertTrue(EtaCalculationService.isMaterialChange(last, candidate, Duration.ofSeconds(60)));
    }
}