package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaView;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryStatusQueryServiceTest {

    @Mock DeliveryJpaRepository deliveries;
    @Mock DeliveryEtaViewRepository etaViews;
    @InjectMocks DeliveryStatusQueryService service;

    private final UUID orderId = UUID.randomUUID();
    private final Delivery delivery =
            Delivery.start(orderId, UUID.randomUUID(), new GeoCoordinate(-34.4, 150.9), 30.0);

    @Test
    void returnsEtaAndObservationTimeWhenAnEtaHasBeenProjected() {
        Instant eta = Instant.parse("2026-10-07T08:10:00Z");
        Instant observed = Instant.parse("2026-10-07T07:55:00Z");
        when(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)).thenReturn(Optional.of(delivery));
        when(etaViews.findById(delivery.getId()))
                .thenReturn(Optional.of(new DeliveryEtaView(delivery.getId(), eta, observed)));

        var status = service.statusForOrder(orderId);

        assertEquals(orderId, status.orderId());
        assertEquals(eta, status.estimatedArrival());
        assertEquals(observed, status.updatedAt());
    }

    @Test
    void etaIsNullWhenDeliveryStartedButNoEtaYet() {
        when(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)).thenReturn(Optional.of(delivery));
        when(etaViews.findById(delivery.getId())).thenReturn(Optional.empty());

        var status = service.statusForOrder(orderId);

        assertNull(status.estimatedArrival());
        assertNull(status.updatedAt());
    }

    @Test
    void riskFlagsAreNullNotEmptyBecauseNoRiskSourceExistsYet() {
        when(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)).thenReturn(Optional.of(delivery));
        when(etaViews.findById(delivery.getId())).thenReturn(Optional.empty());

        assertNull(service.statusForOrder(orderId).riskFlags());
    }

    @Test
    void unknownOrderThrowsNoSuchElement() {
        when(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.statusForOrder(orderId));
    }
}