package com.fleetflow.tracking.infrastructure.messaging;

import java.util.UUID;

/**
 * Internal to tracking-service's own Kafka Streams topology -- deliberately NOT added
 * to fleetflow-contracts or docs/AGENTS.md's event catalogue, since this never crosses
 * a bounded-context boundary; it exists purely to materialize a KTable of active
 * deliveries keyed by courierId, for FR11's stream-table join against
 * CourierLocationUpdated. Worth a short team note/ADR since it's a new internal topic
 * outside the locked §6 catalogue, even though it's not a domain event in the usual
 * sense.
 */
public record ActiveDeliverySnapshot(UUID deliveryId, double destinationLatitude, double destinationLongitude,
                                     double averageSpeedKmh, boolean completed) {
    public ActiveDeliverySnapshot(UUID deliveryId, double destinationLatitude, double destinationLongitude,
                                  double averageSpeedKmh) {
        this(deliveryId, destinationLatitude, destinationLongitude, averageSpeedKmh, false);
    }
}
