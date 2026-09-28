package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierAvailabilityProjectionServiceTest {
    @Mock
    private CourierAvailabilityViewRepository availabilityRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void firstAvailabilityCanPredateRegistrationButOlderShiftUpdatesCannotOverwriteIt() {
        var service = new CourierAvailabilityProjectionService(availabilityRepository, processedEventRepository, transactionManager);
        UUID courierId = UUID.randomUUID();
        Instant registrationTime = Instant.now();
        var view = new CourierAvailabilityView(courierId, "VAN", 3, "ZONE_A", false, registrationTime);
        when(availabilityRepository.findForUpdate(courierId)).thenReturn(Optional.of(view));
        service.applyAvailabilityChange(UUID.randomUUID(), "CourierAvailabilityChanged", courierId, true,
                registrationTime.minusSeconds(5));
        org.junit.jupiter.api.Assertions.assertTrue(view.isAvailable());
        service.applyAvailabilityChange(UUID.randomUUID(), "CourierAvailabilityChanged", courierId, false,
                registrationTime.minusSeconds(10));
        org.junit.jupiter.api.Assertions.assertTrue(view.isAvailable());
    }

    @Test
    void applyAvailabilityChangeDoesNotRecordProcessedEventWhenViewIsMissing() {
        CourierAvailabilityProjectionService service = new CourierAvailabilityProjectionService(availabilityRepository, processedEventRepository, transactionManager);
        UUID eventId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(availabilityRepository.findForUpdate(courierId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.applyAvailabilityChange(eventId, "CourierAvailabilityChanged", courierId, true, Instant.now()));

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        verify(availabilityRepository, never()).save(any(CourierAvailabilityView.class));
    }

    @Test
    void applyAvailabilityChangeRecordsProcessedEventWhenViewExists() {
        CourierAvailabilityProjectionService service = new CourierAvailabilityProjectionService(availabilityRepository, processedEventRepository, transactionManager);
        UUID eventId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        CourierAvailabilityView view = new CourierAvailabilityView(courierId, "VAN", 20, "ZONE_A", false, Instant.now());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(availabilityRepository.findForUpdate(courierId)).thenReturn(Optional.of(view));

        service.applyAvailabilityChange(eventId, "CourierAvailabilityChanged", courierId, true, Instant.now());

        verify(availabilityRepository).save(view);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}
