package com.fleetflow.dispatch.domain;

import java.util.UUID;

/**
 * A courier under consideration for FR7's rule-based selection -- the minimal shape
 * DispatchAssignmentService needs. Built by the application layer from
 * courier_availability_view rows (infrastructure/persistence), never leaked in as
 * the JPA entity itself, since domain/ must not depend on infrastructure types.
 * No operatingZone field: FR7 does not filter on zone in this slice, since
 * OrderPlaced carries no zone data to match against (see FR7's plan/ADR context) --
 * not carried here speculatively.
 */
public record CourierCandidate(UUID courierId, int capacity, GeoCoordinate location) {
}
