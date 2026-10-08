package com.fleetflow.tracking.domain;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
/** Pure domain service: evaluates a rolling window without database or broker access. */
public final class RiskEvaluationService {
    private RiskEvaluationService() { }
    public static List<String> evaluate(List<RiskObservation> observations, double speedKmh,
                                        Instant promisedBy, RiskPolicy policy) {
        if (observations.isEmpty()) return List.of();
        RiskObservation latest = observations.getLast();
        if (GeofenceEvaluationService.isArrived(latest.distanceMeters())) return List.of();
        List<String> reasons = new ArrayList<>();
        Instant eta = latest.capturedAt().plusSeconds(Math.round(latest.distanceMeters() / (speedKmh / 3.6)));
        // SLA needs no minimum window; equality is on time and a missing promise disables only this rule.
        if (promisedBy != null && eta.isAfter(promisedBy)) reasons.add("SLA_BREACH");
        RiskObservation first = observations.getFirst();
        Duration span = Duration.between(first.capturedAt(), latest.capturedAt());
        if (span.compareTo(policy.minimumObservation()) < 0) return List.copyOf(reasons);
        // Sparse pings cannot establish continuous stationary or slow movement.
        for (int i = 1; i < observations.size(); i++) {
            if (Duration.between(observations.get(i - 1).capturedAt(), observations.get(i).capturedAt())
                    .compareTo(policy.maximumPingGap()) > 0) return List.copyOf(reasons);
        }
        boolean stationary = observations.stream().allMatch(p ->
                GeofenceEvaluationService.distanceMeters(first.coordinate(), p.coordinate()) <= policy.stationaryRadiusMeters());
        if (stationary) reasons.add("STATIONARY");
        else {
            double expectedProgress = speedKmh / 3.6 * span.toMillis() / 1000.0;
            if (first.distanceMeters() - latest.distanceMeters() < expectedProgress * policy.minimumProgressRatio())
                reasons.add("OFF_PACE");
        }
        return List.copyOf(reasons);
    }
}
