package com.fleetflow.contracts.fleet;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/** Published by Fleet on FR4. Fields mirror what FR7's rule-based assignment needs to filter on. */
public record CourierRegistered(
        EventEnvelope envelope,
        UUID courierId,
        String vehicleType,
        int capacity,
        String operatingZone) {
}
