package com.fleetflow.tracking.infrastructure.streams;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal to the topology -- the result of joining a location ping against its
 * delivery's snapshot, before the material-change filter decides whether it's worth
 * publishing.
 */
record EtaCandidate(UUID deliveryId, Instant estimatedArrival) {
}