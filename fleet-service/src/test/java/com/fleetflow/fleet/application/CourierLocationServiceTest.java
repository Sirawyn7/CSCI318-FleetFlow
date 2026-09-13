package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.VehicleType;
import com.fleetflow.fleet.infrastructure.messaging.CourierLocationUpdatedEventPublisher;
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
class CourierLocationServiceTest {
    @Mock
    private CourierJpaRepository courierRepository;

    @Mock
    private CourierLocationUpdatedEventPublisher eventPublisher;

    @Test
    void ingestLocationSavesAndPublishesTheUpdatedCourier() {
        CourierLocationService service = new CourierLocationService(courierRepository, eventPublisher);
        Courier courier = Courier.register(VehicleType.VAN, 20, "ZONE_A");
        when(courierRepository.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(courierRepository.save(any(Courier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Courier result = service.ingestLocation(new IngestCourierLocationCommand(courier.getId(), -33.87, 151.21));

        assertEquals(-33.87, result.getCurrentLocation().latitude());
        assertEquals(151.21, result.getCurrentLocation().longitude());

        ArgumentCaptor<Courier> savedCaptor = ArgumentCaptor.forClass(Courier.class);
        verify(courierRepository).save(savedCaptor.capture());
        assertEquals(courier.getId(), savedCaptor.getValue().getId());

        ArgumentCaptor<Courier> publishedCaptor = ArgumentCaptor.forClass(Courier.class);
        verify(eventPublisher).publish(publishedCaptor.capture());
        assertEquals(courier.getId(), publishedCaptor.getValue().getId());
    }

    @Test
    void ingestLocationThrowsWhenCourierNotFound() {
        CourierLocationService service = new CourierLocationService(courierRepository, eventPublisher);
        UUID unknownId = UUID.randomUUID();
        when(courierRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.ingestLocation(new IngestCourierLocationCommand(unknownId, 0, 0)));
    }

    @Test
    void ingestLocationRejectsAnInvalidCoordinate() {
        CourierLocationService service = new CourierLocationService(courierRepository, eventPublisher);
        Courier courier = Courier.register(VehicleType.VAN, 20, "ZONE_A");
        when(courierRepository.findById(courier.getId())).thenReturn(Optional.of(courier));

        assertThrows(IllegalArgumentException.class,
                () -> service.ingestLocation(new IngestCourierLocationCommand(courier.getId(), 999, 0)));
    }

}
