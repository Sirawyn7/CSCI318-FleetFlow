package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.tracking.DeliveryNearDestination;
import com.fleetflow.tracking.domain.GeofenceEvaluationService;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Instant;
import java.util.UUID;

/**
 * FR12: publishes DeliveryNearDestination the first time a delivery crosses the near
 * threshold, and never again for that delivery. Same Processor API justification as
 * FR11's EtaMaterialChangeProcessor: "publish once" needs state across pings, which a
 * plain stateless .filter() can't provide. Backed by a persistent RocksDB store
 * (deliveryId -> a marker string) rather than any JPA table, for the same
 * source-of-truth-vs-derived-state reason as FR11.
 */
public class NearDestinationProcessor
        implements Processor<String, GeofenceCandidate, String, DeliveryNearDestination> {

    static final String STORE_NAME = "near-destination-published-per-delivery";
    private static final String PUBLISHED_MARKER = "PUBLISHED";

    private KeyValueStore<String, String> store;
    private ProcessorContext<String, DeliveryNearDestination> context;

    @Override
    public void init(ProcessorContext<String, DeliveryNearDestination> context) {
        this.context = context;
        this.store = context.getStateStore(STORE_NAME);
    }

    @Override
    public void process(Record<String, GeofenceCandidate> record) {
        GeofenceCandidate candidate = record.value();
        String deliveryKey = candidate.deliveryId().toString();

        if (!GeofenceEvaluationService.isNear(candidate.distanceMeters())) {
            return;
        }
        if (store.get(deliveryKey) != null) {
            return;
        }

        store.put(deliveryKey, PUBLISHED_MARKER);

        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                DeliveryNearDestination.class.getSimpleName(),
                Instant.now(),
                UUID.randomUUID().toString());
        DeliveryNearDestination event = new DeliveryNearDestination(
                envelope, candidate.deliveryId(), candidate.distanceMeters());

        context.forward(new Record<>(deliveryKey, event, record.timestamp()));
    }
}