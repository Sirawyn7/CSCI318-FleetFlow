package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.VehicleAverageSpeed;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class VehicleSpeedConfiguration {
    private final Environment environment;
    public VehicleSpeedConfiguration(Environment environment) { this.environment = environment; }
    public double kmhFor(String vehicleType) {
        Double configured = environment.getProperty("tracking.eta.speed-kmh." + vehicleType.toLowerCase(java.util.Locale.ROOT),
                Double.class);
        return configured != null ? configured : VehicleAverageSpeed.kmhFor(vehicleType);
    }
}
