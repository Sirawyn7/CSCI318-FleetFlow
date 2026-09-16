package com.fleetflow.dispatch.infrastructure.persistence;

/**
 * Which path produced an assignment_decisions row (locked decision 3,
 * docs/AGENTS.md). Only RULE_BASED is reachable until FR8 exists; AGENT is added
 * now so the column supports it later without a schema change (@Enumerated(STRING)
 * on AssignmentDecision.pathUsed).
 */
public enum AssignmentPath {
    RULE_BASED,
    AGENT
}
