package com.fleetflow.contracts.dispatch;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/** Published by Dispatch when both the agent path and the rule-based fallback (FR7/FR8) fail to assign. */
public record AssignmentFailed(EventEnvelope envelope, UUID orderId, String reason) {
}
