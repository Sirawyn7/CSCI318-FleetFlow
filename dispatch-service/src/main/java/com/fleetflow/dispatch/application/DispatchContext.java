package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.GeoCoordinate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable snapshots: no JPA entities, repository access or mutations on the agent thread. */
public record DispatchContext(UUID targetOrderId, Instant now, List<OrderSnapshot> openOrders,
                              List<CourierSnapshot> couriers) {
    public DispatchContext {
        openOrders = List.copyOf(openOrders);
        couriers = List.copyOf(couriers);
    }

    public record OrderSnapshot(UUID orderId, GeoCoordinate pickup, GeoCoordinate dropoff,
                                String serviceLevel, Instant deliveryDeadline, Instant placedAt) { }
    public record CourierSnapshot(UUID courierId, String vehicleType, String operatingZone,
                                  GeoCoordinate location, int capacity, int activeOrderCount,
                                  int pendingAssignments, int remainingCapacity, boolean eligibleForTarget) { }
}
