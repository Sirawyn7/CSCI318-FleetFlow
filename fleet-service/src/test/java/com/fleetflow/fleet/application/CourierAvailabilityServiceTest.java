package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.CourierStatus;
import com.fleetflow.fleet.domain.VehicleType;
import com.fleetflow.fleet.infrastructure.messaging.CourierAvailabilityChangedEventPublisher;
import com.fleetflow.fleet.infrastructure.persistence.CourierJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CourierAvailabilityServiceTest {
    @Mock
    private CourierJpaRepository courierRepository;

    @Mock
    private CourierAvailabilityChangedEventPublisher eventPublisher;

    @Test
    void changeAvailabilitySavesAndPublishesTheUpdateCourier() {
        CourierAvailabilityService service = new CourierAvailabilityService(courierRepository, eventPublisher);
        Courier courier = Courier.register(VehicleType.VAN, 20, "ZONE_A");
        when(courierRepository.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(courierRepository.save(any(Courier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Courier result = service.changeAvailability(new ChangeCourierAvailabilityCommand(courier.getId(), true));

        assertEquals(CourierStatus.AVAILABLE, result.getStatus());

        ArgumentCaptor<Courier> savedCaptor = ArgumentCaptor.forClass(Courier.class);
        verify(courierRepository).save(savedCaptor.capture());
        assertEquals(CourierStatus.AVAILABLE, savedCaptor.getValue().getStatus());

        ArgumentCaptor<Courier> publishedCaptor = ArgumentCaptor.forClass(Courier.class);
        verify(eventPublisher).publish(publishedCaptor.capture());
        assertEquals(courier.getId(), publishedCaptor.getValue().getId());
    }

    @Test
    void changeAvailabilityThrowsWhenCourierNotFound() {
        CourierAvailabilityService service = new CourierAvailabilityService(courierRepository, eventPublisher);
        UUID unknownId = UUID.randomUUID();
        when(courierRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> service.changeAvailability(new ChangeCourierAvailabilityCommand(unknownId, true)));
    }
}
