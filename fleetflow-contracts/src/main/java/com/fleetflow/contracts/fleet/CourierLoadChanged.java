package com.fleetflow.contracts.fleet;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/**
 * Published by Fleet whenever a courier's active-order count changes (FR6,
 * {@code Courier.assignOrder()}/{@code completeOrder()}). Fleet already maintains
 * this count for its own capacity ceiling; this event exposes it so Dispatch can
 * see real load instead of only the registered ceiling (see ADR 0009).
 * loadVersion orders snapshots per courier; acknowledgedOrderId reconciles the
 * corresponding Dispatch reservation even when messages arrive out of order.
 */
public record CourierLoadChanged(EventEnvelope envelope, UUID courierId, int activeOrderCount, int capacityCeiling,
                                 long loadVersion, UUID acknowledgedOrderId) {
    public CourierLoadChanged(EventEnvelope envelope, UUID courierId, int activeOrderCount, int capacityCeiling) {
        this(envelope, courierId, activeOrderCount, capacityCeiling, 0, null);
    }
}
