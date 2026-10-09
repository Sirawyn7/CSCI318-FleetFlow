package com.fleetflow.tracking.domain;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RiskEvaluationServiceTest {
    private final Instant start = Instant.parse("2026-09-29T00:00:00Z");
    private final RiskPolicy policy = new RiskPolicy(Duration.ofMinutes(10), Duration.ofMinutes(5),
            Duration.ofMinutes(2), 30, 0.1, Duration.ofMinutes(10));
    private List<RiskObservation> route(double latitudeStep, double distanceStep) {
        List<RiskObservation> result = new ArrayList<>();
        for (int i=0; i<=5; i++) result.add(new RiskObservation(start.plusSeconds(i*60),
                -33.8 + latitudeStep*i, 151.2, 10000 - distanceStep*i));
        return result;
    }
    @Test void detectsStationaryAfterFiveMinutesWithGpsJitter() {
        assertEquals(List.of("STATIONARY"), RiskEvaluationService.evaluate(route(0.00001, 1), 30, null, policy));
    }
    @Test void detectsMovingAwayAsOffPace() {
        assertEquals(List.of("OFF_PACE"), RiskEvaluationService.evaluate(route(0.001, -100), 30, null, policy));
    }
    @Test void normalProgressDoesNotTrigger() {
        assertTrue(RiskEvaluationService.evaluate(route(0.001, 500), 30, start.plusSeconds(3600), policy).isEmpty());
    }
    @Test void predictsSlaBeforeWindowFillsAndEqualityIsNotABreach() {
        var one = List.of(new RiskObservation(start, -33.8,151.2,1000));
        assertEquals(List.of("SLA_BREACH"), RiskEvaluationService.evaluate(one,36,start.plusSeconds(99),policy));
        assertTrue(RiskEvaluationService.evaluate(one,36,start.plusSeconds(100),policy).isEmpty());
    }
    @Test void sparsePingsDoNotProveStationary() {
        var route=route(0,0);
        assertTrue(RiskEvaluationService.evaluate(List.of(route.getFirst(),route.getLast()),30,null,policy).isEmpty());
    }
    @Test void arrivalSuppressesRiskEvenAfterDeadline() {
        assertTrue(RiskEvaluationService.evaluate(List.of(new RiskObservation(start,-33.8,151.2,50)),30,
                start.minusSeconds(1),policy).isEmpty());
    }
    @Test void shortWindowDoesNotProveAnomaly() {
        assertTrue(RiskEvaluationService.evaluate(route(0,0).subList(0,5),30,null,policy).isEmpty());
    }
    @Test void invalidThresholdsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new RiskPolicy(Duration.ofMinutes(1),
                Duration.ofMinutes(5),Duration.ofMinutes(2),30,0.1,Duration.ofMinutes(10)));
    }
}
