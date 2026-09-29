package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.*;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;

class ActiveDeliverySnapshotRecoveryTest {
    @Test void rebuildsOneSnapshotPerCourierRatherThanOnePerDelivery() {
        var deliveries = mock(DeliveryJpaRepository.class);
        var couriers = mock(CourierVehicleTypeViewRepository.class);
        var publisher = mock(ActiveDeliverySnapshotPublisher.class);
        UUID courier = UUID.randomUUID();
        var first = Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-33.8,151.2), 28);
        var second = Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-33.9,151.2), 28);
        when(deliveries.findAll()).thenReturn(List.of(first,second));
        when(deliveries.findByCourierId(courier)).thenReturn(List.of(first,second));
        when(couriers.findForUpdate(courier)).thenReturn(Optional.of(new CourierVehicleTypeView(courier,"VAN")));
        new ActiveDeliverySnapshotRecovery(deliveries,couriers,publisher).recover();
        verify(publisher).publish(first);
        verifyNoMoreInteractions(publisher);
    }
}
