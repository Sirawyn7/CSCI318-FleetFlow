package com.fleetflow.fleet.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourierTest {

    @Test
    void registerCreatesAnOfflineCourierWithGivenFields() {
        Courier courier = Courier.register(VehicleType.VAN, 20, "ZONE_A");

        assertNotNull(courier.getId());
        assertEquals(VehicleType.VAN, courier.getVehicleType());
        assertEquals(20, courier.getCapacity());
        assertEquals("ZONE_A", courier.getOperatingZone());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
    }

    @Test
    void registerRejectsNullVehicleType() {
        assertThrows(IllegalArgumentException.class, () -> Courier.register(null, 20, "ZONE_A"));
    }

    @Test
    void registerRejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> Courier.register(VehicleType.VAN, 0, "ZONE_A"));
        assertThrows(IllegalArgumentException.class, () -> Courier.register(VehicleType.VAN, -1, "ZONE_A"));
    }

    @Test
    void registerRejectsBlankOperatingZone() {
        assertThrows(IllegalArgumentException.class, () -> Courier.register(VehicleType.VAN, 20, " "));
        assertThrows(IllegalArgumentException.class, () -> Courier.register(VehicleType.VAN, 20, null));
    }

    @Test
    void changeAvailabilityToTrueMakesCourierAvailable() {
        Courier courier = Courier.register(VehicleType.VAN, 20, "ZONE_A");

        courier.changeAvailability(true);

        assertEquals(CourierStatus.AVAILABLE, courier.getStatus());
    }

    @Test
    void changeAvailabilityToFalseMakesCourierOffline() {
        Courier courier = Courier.register(VehicleType.VAN, 20, "ZONE_A");
        courier.changeAvailability(true);

        courier.changeAvailability(false);

        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
    }

    @Test
    void changeAvailabilityIsIdempotent() {
        Courier courier = Courier.register(VehicleType.VAN, 20, "ZONE_A");

        courier.changeAvailability(false);

        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
    }
}
