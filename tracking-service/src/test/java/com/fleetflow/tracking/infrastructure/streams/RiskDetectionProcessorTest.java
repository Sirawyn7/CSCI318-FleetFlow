package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.tracking.DeliveryAtRisk;
import com.fleetflow.tracking.domain.*;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshot;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskDetectionProcessorTest {
    @SuppressWarnings("unchecked")
    @Test void arrivalSurvivesMovementNewAssignmentAndProcessorRestart() {
        ProcessorContext<String, DeliveryAtRisk> context = mock(ProcessorContext.class);
        WindowStore<String, RiskObservation> observations = mock(WindowStore.class);
        KeyValueStore<String, Long> latest = mapStore();
        KeyValueStore<String, Long> alerts = mapStore();
        KeyValueStore<String, Long> arrived = mapStore();
        when(context.getStateStore(RiskDetectionProcessor.WINDOW)).thenReturn(observations);
        when(context.getStateStore(RiskDetectionProcessor.LAST_PING)).thenReturn(latest);
        when(context.getStateStore(RiskDetectionProcessor.ALERTS)).thenReturn(alerts);
        when(context.getStateStore(RiskDetectionProcessor.ARRIVED)).thenReturn(arrived);
        doReturn(mapStore()).when(context).getStateStore(RiskDetectionProcessor.ARRIVAL_COUNT);
        when(observations.fetch(anyString(), any(Instant.class), any(Instant.class))).thenAnswer(call -> {
            WindowStoreIterator<RiskObservation> iterator = mock(WindowStoreIterator.class);
            var sample = new RiskObservation(call.getArgument(2), -33.8, 151.2, 11000);
            when(iterator.hasNext()).thenReturn(true, false);
            when(iterator.next()).thenReturn(KeyValue.pair(sample.capturedAt().toEpochMilli(), sample));
            return iterator;
        });
        var policy = new RiskPolicy(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(2),
                30, 0.1, Duration.ofMinutes(10));
        UUID courier = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-08T00:00:00Z");
        var oldDelivery = snapshot(courier, start);
        var processor = new RiskDetectionProcessor(policy);
        processor.init(context);
        processor.process(ping(oldDelivery, start.plusSeconds(1), -33.9)); // at destination
        processor.process(ping(oldDelivery, start.plusSeconds(2), -33.9));
        verify(arrived, never()).put(anyString(), anyLong());
        // Candidate count also survives restart before confirmation.
        processor = new RiskDetectionProcessor(policy);
        processor.init(context);
        processor.process(ping(oldDelivery, start.plusSeconds(3), -33.9));
        verify(arrived).put(oldDelivery.deliveryId().toString(), start.plusSeconds(3).toEpochMilli());
        // Re-created processor receives the restored stores, as on a task restart.
        processor = new RiskDetectionProcessor(policy);
        processor.init(context);
        for (int minute = 20; minute <= 26; minute++)
            processor.process(ping(oldDelivery, start.plusSeconds(minute * 60), -33.8));
        verify(context, never()).forward(any());
        verify(observations, never()).put(eq(oldDelivery.deliveryId().toString()), any(), anyLong());
        // The same courier's next delivery remains eligible for SLA alerts.
        var nextDelivery = snapshot(courier, start);
        processor.process(ping(nextDelivery, start.plusSeconds(1600), -33.8));
        verify(context).forward(argThat(record -> record.value().deliveryId().equals(nextDelivery.deliveryId())
                && record.value().riskReason().equals("SLA_BREACH")));
    }

    @SuppressWarnings("unchecked")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void interruptedArrivalSequenceDoesNotLatchAndRiskResumes(boolean excessiveGap) {
        ProcessorContext<String, DeliveryAtRisk> context = mock(ProcessorContext.class);
        WindowStore<String, RiskObservation> observations = mock(WindowStore.class);
        KeyValueStore<String, Long> arrived = mapStore();
        when(context.getStateStore(RiskDetectionProcessor.WINDOW)).thenReturn(observations);
        doReturn(mapStore()).when(context).getStateStore(RiskDetectionProcessor.LAST_PING);
        doReturn(mapStore()).when(context).getStateStore(RiskDetectionProcessor.ALERTS);
        when(context.getStateStore(RiskDetectionProcessor.ARRIVED)).thenReturn(arrived);
        doReturn(mapStore()).when(context).getStateStore(RiskDetectionProcessor.ARRIVAL_COUNT);
        when(observations.fetch(anyString(), any(Instant.class), any(Instant.class))).thenAnswer(call -> {
            WindowStoreIterator<RiskObservation> iterator = mock(WindowStoreIterator.class);
            var sample = new RiskObservation(call.getArgument(2), -33.8, 151.2, 11000);
            when(iterator.hasNext()).thenReturn(true, false);
            when(iterator.next()).thenReturn(KeyValue.pair(sample.capturedAt().toEpochMilli(), sample));
            return iterator;
        });
        var policy = new RiskPolicy(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(2),
                30, 0.1, Duration.ofMinutes(10));
        Instant start = Instant.parse("2026-10-08T00:00:00Z");
        var delivery = snapshot(UUID.randomUUID(), start);
        var processor = new RiskDetectionProcessor(policy);
        processor.init(context);
        processor.process(ping(delivery, start.plusSeconds(1), -33.9));
        processor.process(ping(delivery, start.plusSeconds(1), -33.9)); // duplicate cannot count
        processor.process(ping(delivery, start, -33.9)); // stale cannot count
        verify(arrived, never()).put(anyString(), anyLong());
        int resume = excessiveGap ? 123 : 3;
        if (!excessiveGap) processor.process(ping(delivery, start.plusSeconds(2), -33.8));
        processor.process(ping(delivery, start.plusSeconds(resume), -33.9));
        processor.process(ping(delivery, start.plusSeconds(resume + 1), -33.9));
        verify(arrived, never()).put(anyString(), anyLong());
        processor.process(ping(delivery, start.plusSeconds(resume + 2), -33.8));
        verify(context).forward(argThat(record -> record.value().riskReason().equals("SLA_BREACH")));
        // A later uninterrupted sequence still confirms arrival.
        for (int offset = 3; offset <= 5; offset++)
            processor.process(ping(delivery, start.plusSeconds(resume + offset), -33.9));
        verify(arrived).put(delivery.deliveryId().toString(), start.plusSeconds(resume + 5).toEpochMilli());
    }

    private static ActiveDeliverySnapshot snapshot(UUID courier, Instant start) {
        return new ActiveDeliverySnapshot(UUID.randomUUID(), -33.9, 151.2, 28,
                UUID.randomUUID(), courier, start, start);
    }

    private static Record<String, DeliveryLocation> ping(ActiveDeliverySnapshot delivery, Instant time, double latitude) {
        var location = new CourierLocationUpdated(new EventEnvelope(UUID.randomUUID(), "CourierLocationUpdated", time, "test"),
                delivery.courierId(), new GeoPoint(latitude, 151.2), time);
        return new Record<>(delivery.courierId().toString(), new DeliveryLocation(location, delivery), time.toEpochMilli());
    }

    @SuppressWarnings("unchecked")
    private static KeyValueStore<String, Long> mapStore() {
        KeyValueStore<String, Long> store = mock(KeyValueStore.class);
        Map<String, Long> values = new HashMap<>();
        when(store.get(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(store).put(anyString(), anyLong());
        return store;
    }
}
