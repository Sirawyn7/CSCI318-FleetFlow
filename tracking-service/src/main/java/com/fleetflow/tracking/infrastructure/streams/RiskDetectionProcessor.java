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
    static final String ARRIVAL_COUNT = "risk-arrival-count";
    static final long REQUIRED_ARRIVAL_PINGS = 3;
    static final String ALERTS = "risk-last-alert";
    private final RiskPolicy policy;
    private WindowStore<String, RiskObservation> observations;
    private KeyValueStore<String, Long> latest;
    private KeyValueStore<String, Long> alerts;
    private KeyValueStore<String, Long> arrived;
    private KeyValueStore<String, Long> arrivalCount;
    private ProcessorContext<String, DeliveryAtRisk> context;

    public RiskDetectionProcessor(RiskPolicy policy) { this.policy = policy; }

    @Override
    public void init(ProcessorContext<String, DeliveryAtRisk> context) {
        this.context = context;
        observations = context.getStateStore(WINDOW);
        latest = context.getStateStore(LAST_PING);
        alerts = context.getStateStore(ALERTS);
        arrived = context.getStateStore(ARRIVED);
        arrivalCount = context.getStateStore(ARRIVAL_COUNT);
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
        // Require consecutive fresh samples, not a single GPS jitter/pass-by.
        // Both the candidate count and confirmed latch survive task restoration.
        if (GeofenceEvaluationService.isArrived(observation.distanceMeters())) {
            Long count = arrivalCount.get(key);
            long next = count == null || previous == null
                    || time - previous > policy.maximumPingGap().toMillis() ? 1 : count + 1;
            arrivalCount.put(key, next);
            if (next >= REQUIRED_ARRIVAL_PINGS) arrived.put(key, time);
            return;
        }
        arrivalCount.put(key, 0L);
        observations.put(key, observation, time);
        var window = new ArrayList<RiskObservation>();
        Instant windowStart = ping.capturedAt().minus(policy.window());
        if (delivery.startedAt().isAfter(windowStart)) windowStart = delivery.startedAt();
        // Never mix movement from different couriers.
        try (var iterator = observations.fetch(key, windowStart, ping.capturedAt())) {
            while (iterator.hasNext()) window.add(iterator.next().value);
        }
        for (String reason : RiskEvaluationService.evaluate(window, delivery.averageSpeedKmh(), delivery.requestedDeliveryBy(), policy)) {
            // Cooldown per courier: after a reassignment the new courier is judged afresh, so a
            // risk that persists is re-raised (and escalated by FR9) instead of hidden for 10 minutes.
            String alertKey = key + ":" + delivery.courierId() + ":" + reason;
            Long lastAlert = alerts.get(alertKey);
            if (lastAlert != null && time - lastAlert < policy.alertCooldown().toMillis()) continue;
            alerts.put(alertKey, time);
            var envelope = new EventEnvelope(UUID.randomUUID(), "DeliveryAtRisk", Instant.now(), ping.envelope().correlationId());
            context.forward(new Record<>(key, new DeliveryAtRisk(envelope, delivery.deliveryId(), delivery.orderId(),
                    delivery.courierId(), reason), time));
        }
    }
}
