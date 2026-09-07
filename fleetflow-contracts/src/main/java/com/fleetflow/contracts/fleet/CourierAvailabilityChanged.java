package com.fleetflow.contracts.fleet;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/** Published by Fleet on FR5. Also the FR9 offline-mid-delivery trigger for Dispatch. */
public record CourierAvailabilityChanged(EventEnvelope envelope, UUID courierId, boolean available) {
}
