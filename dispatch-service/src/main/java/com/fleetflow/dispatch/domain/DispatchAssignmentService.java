package com.fleetflow.dispatch.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Domain Service (docs/AGENTS.md §9.3: "logic spanning multiple aggregates") --
 * FR7's deterministic courier selection by proximity and remaining capacity.
 * The application filters on availability, known location and configured zone first.
 * Plain Java, no Spring/JPA -- the application layer holds one instance and passes
 * in already-mapped domain types.
 */
public class DispatchAssignmentService {

    /**
     * Selects the best-eligible courier for a pickup location, or empty if none
     * qualify. Deterministic: nearest first, then highest capacity, then lowest
     * courierId, so the same inputs always produce the same output -- required for
     * FR7's "deterministic selection" and for reproducible tests.
     */
    public Optional<CourierCandidate> select(GeoCoordinate pickup, List<CourierCandidate> candidates) {
        return rank(pickup, candidates).stream().findFirst();
    }

    public List<CourierCandidate> rank(GeoCoordinate pickup, List<CourierCandidate> candidates) {
        return candidates.stream()
                // Defensive guard: capacity here means currently unreserved slots.
                .filter(candidate -> candidate.capacity() > 0)
                .sorted(Comparator
                        .comparingDouble((CourierCandidate candidate) -> pickup.distanceKmTo(candidate.location()))
                        .thenComparing(Comparator.comparingInt(CourierCandidate::capacity).reversed())
                        .thenComparing(candidate -> candidate.courierId())).toList();
    }
}
