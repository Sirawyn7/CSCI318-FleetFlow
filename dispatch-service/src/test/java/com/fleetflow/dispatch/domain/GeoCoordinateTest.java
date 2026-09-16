package com.fleetflow.dispatch.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoCoordinateTest {

    @Test
    void distanceToSamePointIsZero() {
        GeoCoordinate point = new GeoCoordinate(-34.4, 150.8);

        assertEquals(0.0, point.distanceKmTo(point), 0.0001);
    }

    @Test
    void distanceForOneDegreeOfLatitudeMatchesKnownHaversineValue() {
        // One degree of latitude is ~111.2km everywhere on a sphere -- a well-known
        // reference value independent of longitude, good for verifying the formula
        // itself rather than a specific pair of real-world coordinates.
        GeoCoordinate origin = new GeoCoordinate(0.0, 0.0);
        GeoCoordinate oneDegreeNorth = new GeoCoordinate(1.0, 0.0);

        assertEquals(111.19, origin.distanceKmTo(oneDegreeNorth), 0.05);
    }

    @Test
    void distanceIsSymmetric() {
        GeoCoordinate a = new GeoCoordinate(-33.8688, 151.2093);
        GeoCoordinate b = new GeoCoordinate(-34.4278, 150.8931);

        assertEquals(a.distanceKmTo(b), b.distanceKmTo(a), 0.0001);
    }
}
