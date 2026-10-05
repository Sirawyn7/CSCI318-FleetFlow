package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.tracking.domain.Delivery;
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

    public ActiveDeliverySnapshotPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publish(Delivery delivery) {
        ActiveDeliverySnapshot snapshot = new ActiveDeliverySnapshot(
                delivery.getId(),
                delivery.getDestination().latitude(),
                delivery.getDestination().longitude(),
                delivery.getAverageSpeedKmh(), delivery.getCompletedAt() != null);

        Message<ActiveDeliverySnapshot> message = MessageBuilder.withPayload(snapshot)
                .setHeader(KafkaHeaders.KEY, delivery.getCourierId().toString())
                .build();

        if (!streamBridge.send(BINDING_NAME, message)) {
            throw new org.springframework.messaging.MessageDeliveryException("Active delivery snapshot publication failed");
        }
    }
}
