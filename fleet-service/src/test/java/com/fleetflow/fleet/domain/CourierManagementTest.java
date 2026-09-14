package com.fleetflow.fleet.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourierManagementTest {
    @Test void capacityAndDeactivationProtectActiveOrders() {
        Courier courier = Courier.register(VehicleType.VAN, 2, "A");
        courier.assignOrder();
        courier.assignOrder();
        assertThrows(IllegalStateException.class, courier::assignOrder);
        assertThrows(IllegalStateException.class, courier::deactivate);
        assertThrows(IllegalStateException.class, () -> courier.updateProfile(VehicleType.VAN, 1, "B"));
        assertEquals(2, courier.getCapacity());
        courier.completeOrder();
        courier.completeOrder();
        assertThrows(IllegalStateException.class, courier::completeOrder);
        courier.deactivate();
        courier.deactivate();
        assertTrue(courier.isDeactivated());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
        assertThrows(IllegalStateException.class, courier::assignOrder);
    }

    @Test void profileUpdateValidatesBeforeMutation() {
        Courier courier = Courier.register(VehicleType.VAN, 2, "A");
        assertThrows(IllegalArgumentException.class, () -> courier.updateProfile(null, 2, "B"));
        assertThrows(IllegalArgumentException.class, () -> courier.updateProfile(VehicleType.VAN, 0, "B"));
        assertThrows(IllegalArgumentException.class, () -> courier.updateProfile(VehicleType.VAN, 3, " "));
        assertEquals("A", courier.getOperatingZone());
        courier.updateProfile(VehicleType.VAN, 3, "B");
        assertEquals(3, courier.getCapacity());
        assertEquals("B", courier.getOperatingZone());
    }
}
