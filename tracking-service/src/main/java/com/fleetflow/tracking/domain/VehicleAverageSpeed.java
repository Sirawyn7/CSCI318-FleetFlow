package com.fleetflow.tracking.domain;

/**
 * Locked defaults for FR11's Haversine x average-speed ETA calculation
 * (requirements-outline.md: "pick defaults now, tune during testing"). Conservative
 * urban averages, not open-road speeds, since FleetFlow deliveries are assumed
 * intra-city. String values mirror fleet-service's VehicleType enum names exactly
 * (CourierRegistered carries vehicleType as a String on the wire, see that record's
 * Javadoc), so kmhFor() intentionally takes a String rather than importing
 * fleet-service's own enum.
 */

public enum VehicleAverageSpeed {
    BICYCLE(15.0),
    MOTORCYCLE(35.0),
    CAR(30.0),
    VAN(28.0),
    TRUCK(22.0);

    private final double kmh;

    VehicleAverageSpeed(double kmh) {
        this.kmh = kmh;
    }

    public double kmh() {
        return kmh;
    }

    public static double kmhFor(String vehicleType) {
        try {
            return valueOf(vehicleType).kmh();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown vehicle type: " + vehicleType, e);
        }
    }
}
