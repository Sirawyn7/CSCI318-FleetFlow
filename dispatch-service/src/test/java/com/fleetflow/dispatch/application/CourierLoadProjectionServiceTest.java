package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;

class CourierLoadProjectionServiceTest {
    @Test void duplicateIsDiscardedBeforeAcquiringCourierLock() {
        var couriers = mock(CourierAvailabilityViewRepository.class);
        var assignments = mock(AssignmentRepository.class);
        var processed = mock(ProcessedEventRepository.class);
        UUID eventId = UUID.randomUUID();
        when(processed.existsById(eventId)).thenReturn(true);
        new CourierLoadProjectionService(couriers, assignments, processed)
                .apply(eventId, "CourierLoadChanged", UUID.randomUUID(), 0, 1, 1, null);
        verifyNoInteractions(couriers, assignments);
        verify(processed, never()).save(any());
    }
}
