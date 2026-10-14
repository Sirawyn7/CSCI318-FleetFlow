package com.fleetflow.tracking.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * Serializes delivery bootstrap and cancellation for one order across topics, and keeps
 * a cancellation tombstone so a late OrderAssigned cannot start a delivery. Same pattern
 * as dispatch-service's OrderCancellationState.
 */
@Entity
@Table(name = "order_cancellation_state")
public class OrderCancellationState {
    @Id private UUID orderId;
    private boolean cancelled;
    @Version private Long version;

    protected OrderCancellationState() { }
    public OrderCancellationState(UUID orderId) { this.orderId = orderId; }

    public boolean isCancelled() { return cancelled; }
    public void cancel() { cancelled = true; }
}
