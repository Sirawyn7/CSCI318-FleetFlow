package com.fleetflow.contracts.dispatch;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/**
 * Published by Dispatch on FR7/FR8. Deliberately does not carry which path (agent vs
 * rule-based) produced the assignment — that is Dispatch-owned audit data for its own
 * {@code assignment_decisions} table, and no consumer in the §6.1 matrix (Tracking,
 * Notification, Fleet, Order) needs it on the wire.
 */
public record OrderAssigned(EventEnvelope envelope, UUID orderId, UUID courierId, UUID assignmentId) {
}
