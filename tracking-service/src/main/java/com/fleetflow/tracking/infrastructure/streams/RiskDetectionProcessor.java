package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.tracking.DeliveryAtRisk;
import com.fleetflow.tracking.domain.*;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/** Rolling event-time aggregation backed by a bounded, changelogged Kafka window store. */
public class RiskDetectionProcessor implements Processor<String, DeliveryLocation, String, DeliveryAtRisk> {
    static final String WINDOW = "risk-observation-window";
    static final String LAST_PING = "risk-last-ping";
    static final String ARRIVED = "risk-arrived";
    static final String ALERTS = "risk-last-alert";
    private final RiskPolicy policy;
    private WindowStore<String, RiskObservation> observations;
    private KeyValueStore<String, Long> latest;
    private KeyValueStore<String, Long> alerts;
    private KeyValueStore<String, Long> arrived;
    private ProcessorContext<String, DeliveryAtRisk> context;

    public RiskDetectionProcessor(RiskPolicy policy) { this.policy = policy; }

    @Override
    public void init(ProcessorContext<String, DeliveryAtRisk> context) {
        this.context = context;
        observations = context.getStateStore(WINDOW);
        latest = context.getStateStore(LAST_PING);
        alerts = context.getStateStore(ALERTS);
        arrived = context.getStateStore(ARRIVED);
    }

    @Override
    public void process(Record<String, DeliveryLocation> record) {
        var input = record.value();
        var delivery = input.delivery();
        var ping = input.location();
        String key = delivery.deliveryId().toString();
        if (arrived.get(key) != null) return;
        long time = ping.capturedAt().toEpochMilli();
        Long previous = latest.get(key);
        if (previous != null && time <= previous) return;
        latest.put(key, time);
        GeoCoordinate current = new GeoCoordinate(ping.location().latitude(), ping.location().longitude());
        GeoCoordinate destination = new GeoCoordinate(delivery.destinationLatitude(), delivery.destinationLongitude());
        RiskObservation observation = new RiskObservation(ping.capturedAt(), current.latitude(), current.longitude(),
                GeofenceEvaluationService.distanceMeters(current, destination));
        // Latch arrival before the next ping: asynchronous snapshot cleanup may lag.
        // This changelogged marker survives restart and old snapshot republication.
        if (GeofenceEvaluationService.isArrived(observation.distanceMeters())) {
            arrived.put(key, time);
            return;
        }
        observations.put(key, observation, time);
        var window = new ArrayList<RiskObservation>();
        try (var iterator = observations.fetch(key, ping.capturedAt().minus(policy.window()), ping.capturedAt())) {
            while (iterator.hasNext()) window.add(iterator.next().value);
        }
        for (String reason : RiskEvaluationService.evaluate(window, delivery.averageSpeedKmh(), delivery.requestedDeliveryBy(), policy)) {
            String alertKey = key + ":" + reason;
            Long lastAlert = alerts.get(alertKey);
            if (lastAlert != null && time - lastAlert < policy.alertCooldown().toMillis()) continue;
            alerts.put(alertKey, time);
            var envelope = new EventEnvelope(UUID.randomUUID(), "DeliveryAtRisk", Instant.now(), ping.envelope().correlationId());
            context.forward(new Record<>(key, new DeliveryAtRisk(envelope, delivery.deliveryId(), delivery.orderId(),
                    delivery.courierId(), reason), time));
        }
    }
}
