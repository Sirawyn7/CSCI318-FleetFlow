package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryStartedEventPublisher;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeView;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationView;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import com.fleetflow.tracking.domain.Delivery;
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
class DeliveryBootstrapServiceTest {

    @Mock
    private OrderDestinationViewRepository destinationRepository;

    @Mock
    private CourierVehicleTypeViewRepository vehicleTypeRepository;

    @Mock
    private DeliveryJpaRepository deliveryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private DeliveryStartedEventPublisher deliveryStartedPublisher;

    @Mock
    private ActiveDeliverySnapshotPublisher activeDeliverySnapshotPublisher;

    private DeliveryBootstrapService service() {
        return new DeliveryBootstrapService(destinationRepository, vehicleTypeRepository, deliveryRepository,
                processedEventRepository, deliveryStartedPublisher, activeDeliverySnapshotPublisher);
    }

    @Test
    void bootstrapCreatesDeliveryAndPublishesBothEventsWhenBothLookupsSucceed() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(destinationRepository.findById(orderId)).thenReturn(
                Optional.of(new OrderDestinationView(orderId, new GeoCoordinate(-33.87, 151.21), Instant.now())));
        when(vehicleTypeRepository.findById(courierId)).thenReturn(
                Optional.of(new CourierVehicleTypeView(courierId, "VAN")));

        service().bootstrap(eventId, "OrderAssigned", orderId, courierId);

        verify(deliveryRepository).save(any(Delivery.class));
        verify(deliveryStartedPublisher).publish(any(Delivery.class));
        verify(activeDeliverySnapshotPublisher).publish(any(Delivery.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void bootstrapDoesNotRecordProcessedEventWhenDestinationIsMissing() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(destinationRepository.findById(orderId)).thenReturn(Optional.empty());
        when(vehicleTypeRepository.findById(courierId)).thenReturn(
                Optional.of(new CourierVehicleTypeView(courierId, "VAN")));

        service().bootstrap(eventId, "OrderAssigned", orderId, courierId);

        verify(deliveryRepository, never()).save(any(Delivery.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void bootstrapDoesNotRecordProcessedEventWhenVehicleTypeIsMissing() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(destinationRepository.findById(orderId)).thenReturn(
                Optional.of(new OrderDestinationView(orderId, new GeoCoordinate(-33.87, 151.21), Instant.now())));
        when(vehicleTypeRepository.findById(courierId)).thenReturn(Optional.empty());

        service().bootstrap(eventId, "OrderAssigned", orderId, courierId);

        verify(deliveryRepository, never()).save(any(Delivery.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }
}