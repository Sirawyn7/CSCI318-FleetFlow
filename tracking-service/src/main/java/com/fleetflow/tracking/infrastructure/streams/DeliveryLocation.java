package com.fleetflow.tracking.infrastructure.streams;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshot;
import com.fleetflow.tracking.infrastructure.messaging.CourierDeliveriesSnapshot;
import java.util.List;
/** Fan out each courier ping to every delivery, excluding pings captured before assignment. */
record DeliveryLocation(CourierLocationUpdated location, ActiveDeliverySnapshot delivery) {
    static List<DeliveryLocation> join(CourierLocationUpdated location, CourierDeliveriesSnapshot snapshot) {
        return snapshot.deliveries().stream()
                .filter(d -> d.startedAt() == null || !location.capturedAt().isBefore(d.startedAt()))
                .map(d -> new DeliveryLocation(location, d)).toList();
    }
}
