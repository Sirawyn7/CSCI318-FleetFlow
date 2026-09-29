package com.fleetflow.tracking.domain;
import java.time.Duration;
public record RiskPolicy(Duration window, Duration minimumObservation, Duration maximumPingGap,
                         double stationaryRadiusMeters, double minimumProgressRatio, Duration alertCooldown) {
    public RiskPolicy {
        if (window == null || minimumObservation == null || maximumPingGap == null || alertCooldown == null
                || window.isNegative() || window.isZero() || minimumObservation.isNegative()
                || minimumObservation.isZero() || minimumObservation.compareTo(window) > 0
                || maximumPingGap.isNegative() || maximumPingGap.isZero()
                || alertCooldown.isNegative() || alertCooldown.isZero()
                || !Double.isFinite(stationaryRadiusMeters) || stationaryRadiusMeters <= 0
                || !Double.isFinite(minimumProgressRatio) || minimumProgressRatio <= 0 || minimumProgressRatio > 1) {
            throw new IllegalArgumentException("Invalid risk detection thresholds");
        }
    }
}
