package com.fleetflow.tracking.infrastructure.messaging;

import java.util.List;

/** Internal courier-keyed projection: a courier may carry several orders concurrently. */
public record CourierDeliveriesSnapshot(List<ActiveDeliverySnapshot> deliveries) {
    public CourierDeliveriesSnapshot {
        deliveries = List.copyOf(deliveries);
    }
}
