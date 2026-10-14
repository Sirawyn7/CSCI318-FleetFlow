package com.fleetflow.tracking.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Derived read model: when Tracking last raised each risk reason for a delivery,
 * projected from its own {@code DeliveryAtRisk} events so the status endpoint can report
 * risk over REST (ADR 0016's ETA view, extended to risk).
 */
@Entity
@Table(name = "delivery_risk_flags")
@IdClass(DeliveryRiskFlag.Key.class)
public class DeliveryRiskFlag {

    @Id
    private UUID deliveryId;

    @Id
    private String reason;

    /** The DeliveryAtRisk event's occurredAt, not when it was projected. */
    private Instant raisedAt;

    protected DeliveryRiskFlag() {
        // JPA
    }

    public DeliveryRiskFlag(UUID deliveryId, String reason, Instant raisedAt) {
        this.deliveryId = deliveryId;
        this.reason = reason;
        this.raisedAt = raisedAt;
    }

    /** Events can be redelivered out of order; an older alert never replaces a newer one. */
    public void raiseIfNewer(Instant at) {
        if (at.isAfter(raisedAt)) raisedAt = at;
    }

    public UUID getDeliveryId() { return deliveryId; }
    public String getReason() { return reason; }
    public Instant getRaisedAt() { return raisedAt; }

    public static class Key implements Serializable {
        private UUID deliveryId;
        private String reason;

        protected Key() { }

        public Key(UUID deliveryId, String reason) {
            this.deliveryId = deliveryId;
            this.reason = reason;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(deliveryId, key.deliveryId)
                    && Objects.equals(reason, key.reason);
        }

        @Override
        public int hashCode() { return Objects.hash(deliveryId, reason); }
    }
}
