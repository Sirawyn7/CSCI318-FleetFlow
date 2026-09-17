package com.fleetflow.dispatch.infrastructure.persistence;

/**
 * Which path produced an assignment_decisions row (locked decision 3,
 * docs/AGENTS.md). AGENT is primary; RULE_BASED records the fallback path.
 */
public enum AssignmentPath {
    RULE_BASED,
    AGENT
}
