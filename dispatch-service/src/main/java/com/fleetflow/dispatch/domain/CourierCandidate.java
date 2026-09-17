package com.fleetflow.dispatch.domain;

import java.util.UUID;

/**
 * A courier under consideration for FR7's rule-based selection -- the minimal shape
 * DispatchAssignmentService needs. Built by the application layer from
 * courier_availability_view rows (infrastructure/persistence), never leaked in as
 * the JPA entity itself, since domain/ must not depend on infrastructure types.
 * Zone eligibility is checked by the application before ranking. Capacity is the
 * remaining number of slots after reported load and local pending reservations.
 */
public record CourierCandidate(UUID courierId, int capacity, GeoCoordinate location) {
}
