package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaView;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeliveryEtaProjectionServiceTest {

    private static final String TYPE = "DeliveryEtaUpdated";

    @Mock DeliveryEtaViewRepository etaRepository;
    @Mock ProcessedEventRepository processedEventRepository;
    @InjectMocks DeliveryEtaProjectionService service;

    private final UUID deliveryId = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2026-10-07T08:00:00Z");

    @Test
    void firstEventCreatesTheViewAndRecordsTheLedgerEntry() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(etaRepository.findById(deliveryId)).thenReturn(Optional.empty());

        service.apply(eventId, TYPE, deliveryId, t0.plusSeconds(600), t0);

        verify(etaRepository).save(any(DeliveryEtaView.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateEventIsIgnored() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        service.apply(eventId, TYPE, deliveryId, t0.plusSeconds(600), t0);

        verify(etaRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void newerEventReplacesTheStoredEta() {
        var view = new DeliveryEtaView(deliveryId, t0.plusSeconds(600), t0);
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(etaRepository.findById(deliveryId)).thenReturn(Optional.of(view));

        service.apply(eventId, TYPE, deliveryId, t0.plusSeconds(900), t0.plusSeconds(30));

        assertEquals(t0.plusSeconds(900), view.getEstimatedArrival());
        assertEquals(t0.plusSeconds(30), view.getObservedAt());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void outOfOrderOlderEventDoesNotOverwriteButIsStillMarkedProcessed() {
        var view = new DeliveryEtaView(deliveryId, t0.plusSeconds(900), t0.plusSeconds(30));
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(etaRepository.findById(deliveryId)).thenReturn(Optional.of(view));

        service.apply(eventId, TYPE, deliveryId, t0.plusSeconds(600), t0);

        assertEquals(t0.plusSeconds(900), view.getEstimatedArrival());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void eventWithNoTimestampDoesNotOverwriteOrCrash() {
        var view = new DeliveryEtaView(deliveryId, t0.plusSeconds(900), t0.plusSeconds(30));
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(etaRepository.findById(deliveryId)).thenReturn(Optional.of(view));

        service.apply(eventId, TYPE, deliveryId, t0.plusSeconds(600), null);

        assertEquals(t0.plusSeconds(900), view.getEstimatedArrival());
        assertEquals(t0.plusSeconds(30), view.getObservedAt());
    }
}
