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

    @Test
    void applyAvailabilityChangeDoesNotRecordProcessedEventWhenViewIsMissing() {
        CourierAvailabilityProjectionService service = new CourierAvailabilityProjectionService(availabilityRepository, processedEventRepository);
        UUID eventId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(availabilityRepository.findById(courierId)).thenReturn(Optional.empty());

        service.applyAvailabilityChange(eventId, "CourierAvailabilityChanged", courierId, true, Instant.now());

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        verify(availabilityRepository, never()).save(any(CourierAvailabilityView.class));
    }

    @Test
    void applyAvailabilityChangeRecordsProcessedEventWhenViewExists() {
        CourierAvailabilityProjectionService service = new CourierAvailabilityProjectionService(availabilityRepository, processedEventRepository);
        UUID eventId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        CourierAvailabilityView view = new CourierAvailabilityView(courierId, "VAN", 20, "ZONE_A", false, Instant.now());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(availabilityRepository.findById(courierId)).thenReturn(Optional.of(view));

        service.applyAvailabilityChange(eventId, "CourierAvailabilityChanged", courierId, true, Instant.now());

        verify(availabilityRepository).save(view);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}
