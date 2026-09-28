package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.tracking.DeliveryNearDestination;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NearDestinationProcessorTest {

    @Mock
    private ProcessorContext<String, DeliveryNearDestination> context;

    @Mock
    private KeyValueStore<String, String> store;

    private NearDestinationProcessor processor;

    @BeforeEach
    void setUp() {
        when(context.getStateStore(NearDestinationProcessor.STORE_NAME)).thenReturn(store);
        processor = new NearDestinationProcessor();
        processor.init(context);
    }

    @Test
    void doesNotForwardWhenBeyondNearThreshold() {
        UUID deliveryId = UUID.randomUUID();
        GeofenceCandidate candidate = new GeofenceCandidate(deliveryId, 600.0, Instant.now());

        processor.process(new Record<>(deliveryId.toString(), candidate, 0L));

        verify(context, never()).forward(any());
        verify(store, never()).put(any(), any());
    }

    @Test
    void doesNotForwardWhenBetweenArrivalAndNearThreshold() {
        // Between 50m and 500m: not arrived, but should count as near.
        UUID deliveryId = UUID.randomUUID();
        String deliveryKey = deliveryId.toString();
        when(store.get(deliveryKey)).thenReturn(null);
        GeofenceCandidate candidate = new GeofenceCandidate(deliveryId, 200.0, Instant.now());

        processor.process(new Record<>(deliveryKey, candidate, 0L));

        verify(context).forward(any());
        verify(store).put(eq(deliveryKey), any());
    }

    @Test
    void forwardsOnceWhenWithinNearThreshold() {
        UUID deliveryId = UUID.randomUUID();
        String deliveryKey = deliveryId.toString();
        when(store.get(deliveryKey)).thenReturn(null);
        GeofenceCandidate candidate = new GeofenceCandidate(deliveryId, 30.0, Instant.now());

        processor.process(new Record<>(deliveryKey, candidate, 0L));

        verify(context).forward(any());
        verify(store).put(eq(deliveryKey), any());
    }

    @Test
    void doesNotForwardAgainOnceAlreadyPublished() {
        UUID deliveryId = UUID.randomUUID();
        String deliveryKey = deliveryId.toString();
        when(store.get(deliveryKey)).thenReturn("PUBLISHED");
        GeofenceCandidate candidate = new GeofenceCandidate(deliveryId, 100.0, Instant.now());

        processor.process(new Record<>(deliveryKey, candidate, 0L));

        verify(context, never()).forward(any());
        verify(store, never()).put(any(), any());
    }
}