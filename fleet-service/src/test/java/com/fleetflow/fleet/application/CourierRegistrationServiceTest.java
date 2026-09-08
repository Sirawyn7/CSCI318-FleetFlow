package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.CourierStatus;
import com.fleetflow.fleet.domain.VehicleType;
import com.fleetflow.fleet.infrastructure.messaging.CourierRegisteredEventPublisher;
import com.fleetflow.fleet.infrastructure.persistence.CourierJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierRegistrationServiceTest {

    @Mock
    private CourierJpaRepository courierRepository;

    @Mock
    private CourierRegisteredEventPublisher eventPublisher;

    @Test
    void registerCourierSavesAndPublishesTheRegisteredCourier() {
        CourierRegistrationService service = new CourierRegistrationService(courierRepository, eventPublisher);
        RegisterCourierCommand command = new RegisterCourierCommand("VAN", 20, "ZONE_A");
        when(courierRepository.save(org.mockito.ArgumentMatchers.any(Courier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Courier result = service.registerCourier(command);

        assertEquals(VehicleType.VAN, result.getVehicleType());
        assertEquals(20, result.getCapacity());
        assertEquals("ZONE_A", result.getOperatingZone());
        assertEquals(CourierStatus.OFFLINE, result.getStatus());

        ArgumentCaptor<Courier> savedCaptor = ArgumentCaptor.forClass(Courier.class);
        verify(courierRepository).save(savedCaptor.capture());
        assertEquals(result.getId(), savedCaptor.getValue().getId());

        ArgumentCaptor<Courier> publishedCaptor = ArgumentCaptor.forClass(Courier.class);
        verify(eventPublisher).publish(publishedCaptor.capture());
        assertEquals(result.getId(), publishedCaptor.getValue().getId());
    }
}
