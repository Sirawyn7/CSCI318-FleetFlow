package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.tracking.DeliveryArrived;
import com.fleetflow.tracking.domain.GeofenceEvaluationService;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Instant;
import java.util.UUID;

/**
 * FR12: publishes DeliveryArrived the first time a delivery crosses the (tighter)
 * arrival threshold, and never again for that delivery. Same shape as
 * NearDestinationProcessor, kept as a separate class/store rather than merged, per
 * the deliberate two-function design (see GeofenceDetectionTopology's Javadoc).
 *
 * arrivedAt is the ping's own capturedAt (carried through GeofenceCandidate), not
 * Instant.now() -- after a restart, rebalance, or backlog replay, "now" would record
 * processing time rather than when the courier actually arrived.
 */
public class ArrivedProcessor implements Processor<String, GeofenceCandidate, String, DeliveryArrived> {

    static final String STORE_NAME = "arrived-published-per-delivery";
    private static final String PUBLISHED_MARKER = "PUBLISHED";

    private KeyValueStore<String, String> store;
    private ProcessorContext<String, DeliveryArrived> context;

    @Override
    public void init(ProcessorContext<String, DeliveryArrived> context) {
        this.context = context;
        this.store = context.getStateStore(STORE_NAME);
    }

    @Override
    public void process(Record<String, GeofenceCandidate> record) {
        GeofenceCandidate candidate = record.value();
        String deliveryKey = candidate.deliveryId().toString();

        if (!GeofenceEvaluationService.isArrived(candidate.distanceMeters())) {
            return;
        }
        if (store.get(deliveryKey) != null) {
            return;
        }

        store.put(deliveryKey, PUBLISHED_MARKER);

        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                DeliveryArrived.class.getSimpleName(),
                Instant.now(),
                UUID.randomUUID().toString());
        DeliveryArrived event = new DeliveryArrived(envelope, candidate.deliveryId(), candidate.capturedAt());

        context.forward(new Record<>(deliveryKey, event, record.timestamp()));
    }
}