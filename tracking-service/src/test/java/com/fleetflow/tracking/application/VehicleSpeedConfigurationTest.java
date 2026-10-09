package com.fleetflow.tracking.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class VehicleSpeedConfigurationTest {
    @Test void customVehicleUsesConfiguredSpeedWithoutLookingUpHardcodedDefault() {
        var speeds = new VehicleSpeedConfiguration(new MockEnvironment()
                .withProperty("tracking.eta.speed-kmh.cargo_bike", "18"));
        assertEquals(18, speeds.kmhFor("CARGO_BIKE"));
    }

    @Test void configuredSpeedOverridesDefaultWithoutChangingOtherVehicles() {
        var speeds = new VehicleSpeedConfiguration(new MockEnvironment().withProperty("tracking.eta.speed-kmh.van", "24"));
        assertEquals(24, speeds.kmhFor("VAN"));
        assertEquals(15, speeds.kmhFor("BICYCLE"));
        assertThrows(IllegalArgumentException.class, () -> speeds.kmhFor("UNKNOWN"));
    }
}
