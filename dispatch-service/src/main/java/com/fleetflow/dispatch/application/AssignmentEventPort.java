package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.Assignment;

import java.util.UUID;

/**
 * Port implemented by infrastructure/messaging (mirrors order-service's
 * OrderEventPort: pass the domain aggregate, not raw fields, and let the publisher
 * extract what it needs). Keeps contracts imports out of application/, consistent
 * with docs/decisions/0004 rule 4 and ContractBoundaryTest. No aggregate exists for
 * a failed attempt, so publishAssignmentFailed stays plain params rather than
 * inventing one just for symmetry.
 */
public interface AssignmentEventPort {

    void publishOrderAssigned(Assignment assignment);

    void publishAssignmentFailed(UUID orderId, String reason);

    void publishOrderReassigned(Assignment assignment);
}
