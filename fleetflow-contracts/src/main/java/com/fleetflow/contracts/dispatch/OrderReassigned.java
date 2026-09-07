package com.fleetflow.contracts.dispatch;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/** Published by Dispatch on FR9, subject to the one-reassignment-then-cooldown loop guard. */
public record OrderReassigned(
        EventEnvelope envelope,
        UUID orderId,
        UUID previousCourierId,
        UUID newCourierId,
        UUID assignmentId,
        String reason) {
}
