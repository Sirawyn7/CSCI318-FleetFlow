package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.dispatch.AssignmentFailed;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.dispatch.application.AssignmentEventPort;
import com.fleetflow.dispatch.domain.Assignment;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Implements AssignmentEventPort using StreamBridge, same shape as order-service's
 * OrderEventPublisher: throw on a failed send, since OrderAssigned matters to
 * Fleet/Tracking/Notification/Order per requirements-outline.md §6.1.
 */
@Component
public class AssignmentEventPublisher implements AssignmentEventPort {

    private final StreamBridge streamBridge;

    @Override
    public void publishOrderReassigned(Assignment assignment) {
        var envelope = new EventEnvelope(assignment.getReassignmentEventId(), "OrderReassigned",
                assignment.getReassignedAt(), assignment.getOrderId().toString());
        var event = new com.fleetflow.contracts.dispatch.OrderReassigned(envelope,
                assignment.getOrderId(), assignment.getPreviousCourierId(), assignment.getCourierId(),
                assignment.getId(), assignment.getExceptionReason());
        if (!streamBridge.send("orderReassigned-out-0", event))
            throw new IllegalStateException("OrderReassigned publication failed");
    }

    public AssignmentEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publishOrderAssigned(Assignment assignment) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                "OrderAssigned",
                Instant.now(),
                assignment.getOrderId().toString());

        OrderAssigned event = new OrderAssigned(
                envelope,
                assignment.getOrderId(),
                assignment.getCourierId(),
                assignment.getId());

        if (!streamBridge.send("orderAssigned-out-0", event)) {
            throw new IllegalStateException("OrderAssigned publication failed");
        }
    }

    @Override
    public void publishAssignmentFailed(UUID orderId, String reason) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                "AssignmentFailed",
                Instant.now(),
                orderId.toString());

        AssignmentFailed event = new AssignmentFailed(envelope, orderId, reason);

        if (!streamBridge.send("assignmentFailed-out-0", event)) {
            throw new IllegalStateException("AssignmentFailed publication failed");
        }
    }
}
