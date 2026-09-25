package com.fleetflow.tracking.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeofenceEvaluationServiceTest {

    @Test
    void distanceMetersOfIdenticalPointsIsZero() {
        GeoCoordinate point = new GeoCoordinate(-33.8688, 151.2093);

        assertEquals(0.0, GeofenceEvaluationService.distanceMeters(point, point), 0.01);
    }

    @Test
    void isNearIsTrueAtExactlyTheThreshold() {
        assertTrue(GeofenceEvaluationService.isNear(500.0));
    }

    @Test
    void isNearIsFalseJustBeyondTheThreshold() {
        assertFalse(GeofenceEvaluationService.isNear(500.01));
    }

    @Test
    void isArrivedIsTrueAtExactlyTheThreshold() {
        assertTrue(GeofenceEvaluationService.isArrived(50.0));
    }

    @Test
    void isArrivedIsFalseJustBeyondTheThreshold() {
        assertFalse(GeofenceEvaluationService.isArrived(50.01));
    }

    @Test
    void arrivedIsAlwaysAlsoNear() {
        // The arrival radius must stay nested inside the near radius -- otherwise a
        // courier could "arrive" without ever having crossed "near" first. This says
        // nothing about publish ordering between the two topology functions
        // themselves: they're independent, write to different topics, and consumers
        // should not assume DeliveryNearDestination always arrives before
        // DeliveryArrived.
        assertTrue(GeofenceEvaluationService.ARRIVAL_THRESHOLD_METERS
                <= GeofenceEvaluationService.NEAR_THRESHOLD_METERS);
    }
}
