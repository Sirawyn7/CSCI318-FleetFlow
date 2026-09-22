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
    void isArrivedIsTrueJustBeyondTheThreshold() {
        assertFalse(GeofenceEvaluationService.isArrived(50.01));
    }

    @Test
    void arrivedIsAlwaysAlsoNear() {
        // ARRIVAL_THRESHOLD_METERS must stay within NEAR_THRESHOLD_METERS -- otherwise
        // a courier could "arrive" without ever having been "near", breaking the
        // publish-order assumption in requirements-outline.md FR12 ("publishes
        // DeliveryNearDestination then DeliveryArrived").
        assertTrue(GeofenceEvaluationService.ARRIVAL_THRESHOLD_METERS
                <= GeofenceEvaluationService.NEAR_THRESHOLD_METERS);
    }
}
