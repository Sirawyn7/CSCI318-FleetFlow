package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.tracking.DeliveryEtaUpdated;
import com.fleetflow.tracking.domain.EtaCalculationService;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka Streams Processor API (not the plain DSL) because "only republish on material
 * change" needs per-delivery state across pings -- a stateless .filter() can't compare
 * against the previous emission. Backed by a persistent RocksDB state store (keyed by
 * deliveryId as String, value = epoch millis) rather than the deliveries JPA table:
 * this is hot-path state read/written on every location ping, and hitting the database
 * that often from a Streams processing thread would add unnecessary I/O latency to the
 * topology -- this is derived, ephemeral operational state, not the source-of-truth
 * delivery record (see AGENTS.md's source-of-truth vs derived-state distinction,
 * explicitly assessed).
 *
 * Re-keys the output from courierId (the join key) to deliveryId, since
 * DeliveryEtaUpdated is about a specific delivery, not a courier.
 */
public class EtaMaterialChangeProcessor implements Processor<String, EtaCandidate, String, DeliveryEtaUpdated> {

    static final String PING_STORE = "eta-last-ping-per-delivery";
    static final String STORE_NAME = "last-eta-per-delivery";
    static final Duration MATERIAL_CHANGE_THRESHOLD = Duration.ofSeconds(60);

    private KeyValueStore<String, Long> store;
    private KeyValueStore<String, Long> lastPing;
    private ProcessorContext<String, DeliveryEtaUpdated> context;

    @Override
    public void init(ProcessorContext<String, DeliveryEtaUpdated> context) {
        this.context = context;
        this.store = context.getStateStore(STORE_NAME);
        this.lastPing = context.getStateStore(PING_STORE);
    }

    @Override
    public void process(Record<String, EtaCandidate> record) {
        EtaCandidate candidate = record.value();
        UUID deliveryId = candidate.deliveryId();
        String deliveryKey = deliveryId.toString();

        Long previousPing = lastPing.get(deliveryKey);
        long captured = candidate.capturedAt().toEpochMilli();
        if (previousPing != null && captured <= previousPing) return;
        lastPing.put(deliveryKey, captured);
        Long lastMillis = store.get(deliveryKey);
        Instant lastPublished = lastMillis == null ? null : Instant.ofEpochMilli(lastMillis);

        if (!EtaCalculationService.isMaterialChange(lastPublished, candidate.estimatedArrival(),
                MATERIAL_CHANGE_THRESHOLD)) {
            return;
        }

        store.put(deliveryKey, candidate.estimatedArrival().toEpochMilli());

        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                DeliveryEtaUpdated.class.getSimpleName(),
                Instant.now(),
                UUID.randomUUID().toString());
        DeliveryEtaUpdated event = new DeliveryEtaUpdated(envelope, deliveryId, candidate.estimatedArrival());

        context.forward(new Record<>(deliveryKey, event, record.timestamp()));
    }
}
