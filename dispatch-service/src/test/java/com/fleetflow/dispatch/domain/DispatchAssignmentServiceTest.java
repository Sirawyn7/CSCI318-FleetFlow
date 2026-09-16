package com.fleetflow.dispatch.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchAssignmentServiceTest {

    private final DispatchAssignmentService service = new DispatchAssignmentService();
    private final GeoCoordinate pickup = new GeoCoordinate(-34.4, 150.8);

    @Test
    void selectsNoOneWhenCandidateListIsEmpty() {
        Optional<CourierCandidate> selected = service.select(pickup, List.of());

        assertTrue(selected.isEmpty());
    }

    @Test
    void selectsNoOneWhenNoCandidateHasPositiveCapacity() {
        CourierCandidate zeroCapacity = new CourierCandidate(UUID.randomUUID(), 0, pickup);

        Optional<CourierCandidate> selected = service.select(pickup, List.of(zeroCapacity));

        assertTrue(selected.isEmpty());
    }

    @Test
    void selectsTheNearestEligibleCandidate() {
        CourierCandidate nearby = new CourierCandidate(UUID.randomUUID(), 5, new GeoCoordinate(-34.41, 150.81));
        CourierCandidate faraway = new CourierCandidate(UUID.randomUUID(), 5, new GeoCoordinate(-33.86, 151.20));

        Optional<CourierCandidate> selected = service.select(pickup, List.of(faraway, nearby));

        assertEquals(nearby.courierId(), selected.orElseThrow().courierId());
    }

    @Test
    void breaksProximityTiesByHigherCapacity() {
        CourierCandidate lowCapacity = new CourierCandidate(UUID.randomUUID(), 3, pickup);
        CourierCandidate highCapacity = new CourierCandidate(UUID.randomUUID(), 10, pickup);

        Optional<CourierCandidate> selected = service.select(pickup, List.of(lowCapacity, highCapacity));

        assertEquals(highCapacity.courierId(), selected.orElseThrow().courierId());
    }

    @Test
    void breaksProximityAndCapacityTiesByLowestCourierId() {
        // Both UUIDs share the same (non-negative) most-significant bits, so this
        // reduces to a plain least-significant-bits comparison -- UUID.compareTo
        // treats both halves as SIGNED longs, so a naive "hex string order" pick
        // (e.g. 000...1 vs fff...f) would be wrong: fff...f's MSB is negative as a
        // signed long and would sort as "lower" than 000...1's.
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CourierCandidate lower = new CourierCandidate(lowerId, 5, pickup);
        CourierCandidate higher = new CourierCandidate(higherId, 5, pickup);

        Optional<CourierCandidate> selected = service.select(pickup, List.of(higher, lower));

        assertEquals(lowerId, selected.orElseThrow().courierId());
    }
}
