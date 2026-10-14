package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaView;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlag;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryStatusQueryServiceTest {

    @Mock DeliveryJpaRepository deliveries;
    @Mock DeliveryEtaViewRepository etaViews;
    @Mock DeliveryRiskFlagRepository riskFlags;

    private final UUID orderId = UUID.randomUUID();
    private final Delivery delivery =
            Delivery.start(orderId, UUID.randomUUID(), new GeoCoordinate(-34.4, 150.9), 30.0);
    private final Instant base = delivery.getStartedAt();

    /** Freshness of 12 minutes, as with the default 10-minute cooldown and 2-minute ping gap. */
    private DeliveryStatusQueryService serviceAt(Instant now) {
        return new DeliveryStatusQueryService(deliveries, etaViews, riskFlags, Duration.ofMinutes(12),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void deliveryExists() {
        when(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)).thenReturn(Optional.of(delivery));
        lenient().when(etaViews.findById(delivery.getId())).thenReturn(Optional.empty());
    }

    private DeliveryRiskFlag flag(String reason, Instant at) {
        return new DeliveryRiskFlag(delivery.getId(), reason, at);
    }

    @Test
    void returnsEtaAndObservationTimeWhenAnEtaHasBeenProjected() {
        Instant eta = Instant.parse("2026-10-07T08:10:00Z");
        Instant observed = Instant.parse("2026-10-07T07:55:00Z");
        when(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)).thenReturn(Optional.of(delivery));
        when(etaViews.findById(delivery.getId()))
                .thenReturn(Optional.of(new DeliveryEtaView(delivery.getId(), eta, observed)));

        var status = serviceAt(base).statusForOrder(orderId);

        assertEquals(orderId, status.orderId());
        assertEquals(eta, status.estimatedArrival());
        assertEquals(observed, status.updatedAt());
    }

    @Test
    void etaIsNullWhenDeliveryStartedButNoEtaYet() {
        deliveryExists();

        var status = serviceAt(base).statusForOrder(orderId);

        assertNull(status.estimatedArrival());
        assertNull(status.updatedAt());
    }

    @Test
    void noRiskAlertsMeansAnEmptyListNotNull() {
        deliveryExists();

        assertEquals(List.of(), serviceAt(base).statusForOrder(orderId).riskFlags());
    }

    @Test
    void reportsOnlyReasonsRaisedWithinTheFreshnessWindowSorted() {
        deliveryExists();
        Instant now = base.plus(Duration.ofMinutes(30));
        when(riskFlags.findByDeliveryId(delivery.getId())).thenReturn(List.of(
                flag("STATIONARY", now.minus(Duration.ofMinutes(3))),
                flag("SLA_BREACH", now.minus(Duration.ofMinutes(11))),
                flag("OFF_PACE", now.minus(Duration.ofMinutes(13)))));

        assertEquals(List.of("SLA_BREACH", "STATIONARY"), serviceAt(now).statusForOrder(orderId).riskFlags());
    }

    @Test
    void alertsRaisedForAPreviousCourierAreNotReported() {
        deliveryExists();
        ReflectionTestUtils.setField(delivery, "courierAssignedAt", base.plus(Duration.ofMinutes(20)));
        Instant now = base.plus(Duration.ofMinutes(25));
        when(riskFlags.findByDeliveryId(delivery.getId())).thenReturn(List.of(
                flag("STATIONARY", base.plus(Duration.ofMinutes(15))),
                flag("SLA_BREACH", base.plus(Duration.ofMinutes(22)))));

        assertEquals(List.of("SLA_BREACH"), serviceAt(now).statusForOrder(orderId).riskFlags());
    }

    @Test
    void endedDeliveryReportsNoCurrentRisk() {
        deliveryExists();
        delivery.arrive(base.plusSeconds(60));

        assertEquals(List.of(), serviceAt(base.plusSeconds(120)).statusForOrder(orderId).riskFlags());
    }

    @Test
    void unknownOrderThrowsNoSuchElement() {
        when(deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> serviceAt(base).statusForOrder(orderId));
    }
}
