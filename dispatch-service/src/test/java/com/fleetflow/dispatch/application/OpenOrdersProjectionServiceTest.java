package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the branching logic the reference CourierRegistered slice never needed a unit
 * test for: insert vs. delete vs. the idempotent no-op in each direction.
 */
@ExtendWith(MockitoExtension.class)
class OpenOrdersProjectionServiceTest {

    @Mock
    private OpenOrderViewRepository openOrderViewRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private OpenOrdersProjectionService service;

    @BeforeEach
    void setUp() {
        service = new OpenOrdersProjectionService(openOrderViewRepository, processedEventRepository);
    }

    @Test
    void applyOrderPlacedInsertsRowAndMarksEventProcessed() {
        UUID eventId = UUID.randomUUID();
        OpenOrderView snapshot = new OpenOrderView(
                UUID.randomUUID(), UUID.randomUUID(), 1.0, 2.0, 3.0, 4.0,
                "STANDARD", Instant.now(), Instant.now());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        service.applyOrderPlaced(eventId, "OrderPlaced", snapshot);

        verify(openOrderViewRepository).save(snapshot);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void applyOrderPlacedIsNoOpWhenEventAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        OpenOrderView snapshot = new OpenOrderView(
                UUID.randomUUID(), UUID.randomUUID(), 1.0, 2.0, 3.0, 4.0,
                "STANDARD", Instant.now(), Instant.now());
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        service.applyOrderPlaced(eventId, "OrderPlaced", snapshot);

        verify(openOrderViewRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void applyOrderCancelledDeletesExistingRowAndMarksEventProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(openOrderViewRepository.existsById(orderId)).thenReturn(true);

        service.applyOrderCancelled(eventId, "OrderCancelled", orderId);

        verify(openOrderViewRepository).deleteById(orderId);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void applyOrderCancelledIsSafeNoOpWhenRowAlreadyAbsent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(openOrderViewRepository.existsById(orderId)).thenReturn(false);

        service.applyOrderCancelled(eventId, "OrderCancelled", orderId);

        verify(openOrderViewRepository, never()).deleteById(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void applyOrderCancelledIsNoOpWhenEventAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        service.applyOrderCancelled(eventId, "OrderCancelled", orderId);

        verify(openOrderViewRepository, never()).existsById(any());
        verify(openOrderViewRepository, never()).deleteById(any());
        verify(processedEventRepository, never()).save(any());
    }
}
