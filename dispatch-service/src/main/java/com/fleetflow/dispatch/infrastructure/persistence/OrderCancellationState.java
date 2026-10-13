package com.fleetflow.dispatch.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Serializes placement/cancellation across topics and retains cancellation tombstones. */
@Entity
public class OrderCancellationState {
    @Id private UUID orderId;
    private boolean cancelled;
    @jakarta.persistence.Version private Long version;
    protected OrderCancellationState() { }
    public OrderCancellationState(UUID orderId) { this.orderId = orderId; }
    public boolean isCancelled() { return cancelled; }
    public void cancel() { cancelled = true; }
}
