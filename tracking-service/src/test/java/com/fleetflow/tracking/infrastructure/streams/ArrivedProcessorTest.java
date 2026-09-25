package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.tracking.DeliveryArrived;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivedProcessorTest {

    @Mock
    private ProcessorContext<String, DeliveryArrived> context;

    @Mock
    private KeyValueStore<String, String> store;

    private ArrivedProcessor processor;

    @BeforeEach
    void setUp() {
        when(context.getStateStore(ArrivedProcessor.STORE_NAME)).thenReturn(store);
        processor = new ArrivedProcessor();
        processor.init(context);
    }

    @Test
    void doesNotForwardWhenBeyondArrivalThreshold() {
        // 200m: within the 500m near radius but outside the 50m arrival radius.
        UUID deliveryId = UUID.randomUUID();
        GeofenceCandidate candidate = new GeofenceCandidate(deliveryId, 200.0, Instant.now());

        processor.process(new Record<>(deliveryId.toString(), candidate, 0L));

        verify(context, never()).forward(any());
        verify(store, never()).put(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardsOnceWhenWithinArrivalThresholdAndUsesPingCapturedAt() {
        UUID deliveryId = UUID.randomUUID();
        String deliveryKey = deliveryId.toString();
        Instant capturedAt = Instant.parse("2026-01-01T00:00:00Z");
        when(store.get(deliveryKey)).thenReturn(null);
        GeofenceCandidate candidate = new GeofenceCandidate(deliveryId, 25.0, capturedAt);

        processor.process(new Record<>(deliveryKey, candidate, 0L));

        ArgumentCaptor<Record<String, DeliveryArrived>> captor = ArgumentCaptor.forClass(Record.class);
        verify(context).forward(captor.capture());
        assertEquals(capturedAt, captor.getValue().value().arrivedAt());
        verify(store).put(eq(deliveryKey), any());
    }

    @Test
    void doesNotForwardAgainOnceAlreadyPublished() {
        UUID deliveryId = UUID.randomUUID();
        String deliveryKey = deliveryId.toString();
        when(store.get(deliveryKey)).thenReturn("PUBLISHED");
        GeofenceCandidate candidate = new GeofenceCandidate(deliveryId, 10.0, Instant.now());

        processor.process(new Record<>(deliveryKey, candidate, 0L));

        verify(context, never()).forward(any());
        verify(store, never()).put(any(), any());
    }
}