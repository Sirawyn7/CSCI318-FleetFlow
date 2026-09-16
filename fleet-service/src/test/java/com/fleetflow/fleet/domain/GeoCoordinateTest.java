package com.fleetflow.fleet.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoCoordinateTest {
    @Test
    void rejectsLatitudeOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(-91, 0));
    }

    @Test
    void rejectsLongitudeOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0, 181));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0, -181));
    }

    @Test
    void acceptsBoundaryValues() {
        GeoCoordinate coordinate = new GeoCoordinate(90, 180);

        assertEquals(90, coordinate.latitude());
        assertEquals(180, coordinate.longitude());
    }
}
