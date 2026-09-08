package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer (docs/decisions/0004, rule 4): maps Fleet's published
 * CourierRegistered into Dispatch's own courier_availability_view row, and nothing
 * else — no persistence, no idempotency check.
 */
@Component
public class CourierRegisteredTranslator {

    public CourierAvailabilityView translate(CourierRegistered event) {
        return new CourierAvailabilityView(
                event.courierId(),
                event.vehicleType(),
                event.capacity(),
                event.operatingZone(),
                // Not available until Fleet's CourierAvailabilityChanged (FR5) is
                // consumed — that event isn't wired into this slice yet.
                false,
                event.envelope().occurredAt());
    }
}
