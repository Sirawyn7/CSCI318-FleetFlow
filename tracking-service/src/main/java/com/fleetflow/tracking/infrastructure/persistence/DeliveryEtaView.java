package com.fleetflow.tracking.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Derived read model, not source-of-truth: the latest ETA Tracking itself published
 * for a delivery (FR11), projected from {@code DeliveryEtaUpdated}. Exists because the
 * ETA otherwise lives only in Kafka Streams state, which is not queryable over REST
 * (see docs/decisions/0014-tracking-status-read-model.md).
 */

@Entity
@Table(name = "delivery_eta_view")
public class DeliveryEtaView {

    @Id
    private UUID deliveryId;

    private Instant estimatedArrival;

    /** When the ETA was calculated (the event's occurredAt), not when it was projected. */
    private Instant observedAt;

    protected DeliveryEtaView() {
        // JPA
    }

    public DeliveryEtaView(UUID deliveryId, Instant estimatedArrival, Instant observedAt) {
        this.deliveryId = deliveryId;
        this.estimatedArrival = estimatedArrival;
        this.observedAt = observedAt;
    }

    /** Kafka does not order events across restarts/rebalances; never let an older ETA win. */
    public boolean replaceIfNewer(Instant newEstimatedArrival, Instant newObservedAt) {
        if (observedAt != null && !newObservedAt.isAfter(observedAt)) {
            return false;
        }
        this.estimatedArrival = newEstimatedArrival;
        this.observedAt = newObservedAt;
        return true;
    }

    public UUID getDeliveryId() { return deliveryId; }
    public Instant getEstimatedArrival() { return estimatedArrival; }
    public Instant getObservedAt() { return observedAt; }
}
