package com.fleetflow.contracts.order;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/** Published by Order on FR3. */
public record OrderCancelled(EventEnvelope envelope, UUID orderId, String reason) {
}
