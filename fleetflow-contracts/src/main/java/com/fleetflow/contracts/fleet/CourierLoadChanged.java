package com.fleetflow.contracts.fleet;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/**
 * Published by Fleet whenever a courier's active-order count changes (FR6,
 * {@code Courier.assignOrder()}/{@code completeOrder()}). Fleet already maintains
 * this count for its own capacity ceiling; this event exposes it so Dispatch can
 * eventually see real load instead of only the registered ceiling (see ADR 0009).
 * No consumer is wired yet -- added catalogue-first per docs/AGENTS.md.
 */
public record CourierLoadChanged(EventEnvelope envelope, UUID courierId, int activeOrderCount, int capacityCeiling) {
}
