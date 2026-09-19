package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.GeoCoordinate;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OperatingZonesTest {
    @Test void adjacentZonesHaveOnlyOneOwnerOfTheirSharedEdge() {
        var south = new OperatingZones.Bounds(-35, -34, 150, 151);
        var north = new OperatingZones.Bounds(-34, -33, 150, 151);
        var east = new OperatingZones.Bounds(-35, -34, 151, 152);
        assertFalse(south.contains(new GeoCoordinate(-34, 150.5)));
        assertTrue(north.contains(new GeoCoordinate(-34, 150.5)));
        assertFalse(south.contains(new GeoCoordinate(-34.5, 151)));
        assertTrue(east.contains(new GeoCoordinate(-34.5, 151)));
    }
    @Test void zoneUsesPickupCoordinatesAndRejectsUnknownOrOutsideAreas() {
        var zones = new OperatingZones();
        zones.setZones(Map.of("A", new OperatingZones.Bounds(-35, -34, 150, 151)));
        assertTrue(zones.serves("A", new GeoCoordinate(-34.4, 150.8)));
        assertTrue(zones.serves("A", new GeoCoordinate(-35, 150)));
        assertFalse(zones.serves("A", new GeoCoordinate(-33.9, 150.8)));
        assertFalse(zones.serves("B", new GeoCoordinate(-34.4, 150.8)));
        assertThrows(IllegalArgumentException.class, () -> new OperatingZones.Bounds(-34, -35, 150, 151));
        assertThrows(IllegalArgumentException.class, () -> new OperatingZones.Bounds(Double.NaN, -35, 150, 151));
    }
}
