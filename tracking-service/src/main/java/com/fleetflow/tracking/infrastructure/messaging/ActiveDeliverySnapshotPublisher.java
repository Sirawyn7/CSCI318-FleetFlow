package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publishes to the internal active-delivery-by-courier topic that FR11's Streams
 * topology materializes as a KTable (see ActiveDeliverySnapshot's Javadoc). Keyed by
 * courierId, same as CourierLocationUpdated, so the two are co-partitioned for the
 * stream-table join.
 */
@Component
public class ActiveDeliverySnapshotPublisher {

    private static final String BINDING_NAME = "activeDeliverySnapshot-out-0";

    private final StreamBridge streamBridge;
    private final DeliveryJpaRepository deliveries;

    public ActiveDeliverySnapshotPublisher(StreamBridge streamBridge, DeliveryJpaRepository deliveries) {
        this.streamBridge = streamBridge;
        this.deliveries = deliveries;
    }

    public void publish(Delivery delivery) {
        publish(delivery.getCourierId());
    }

    /** An empty list clears the courier's materialized snapshot after the final arrival or completion. */
    public void publish(java.util.UUID courierId) {
        CourierDeliveriesSnapshot snapshot = new CourierDeliveriesSnapshot(
                deliveries.findByCourierIdAndArrivedAtIsNullAndCompletedAtIsNullAndCancelledAtIsNull(courierId).stream().map(d -> new ActiveDeliverySnapshot(
                        d.getId(), d.getDestination().latitude(), d.getDestination().longitude(),
                        d.getAverageSpeedKmh(), d.getOrderId(), d.getCourierId(),
                        d.getRequestedDeliveryBy(), d.getMonitoringStartedAt())).toList());

        Message<CourierDeliveriesSnapshot> message = MessageBuilder.withPayload(snapshot)
                .setHeader(KafkaHeaders.KEY, courierId.toString())
                .build();

        if (!streamBridge.send(BINDING_NAME, message)) {
            throw new IllegalStateException("Active delivery snapshot publication failed");
        }
    }
}
