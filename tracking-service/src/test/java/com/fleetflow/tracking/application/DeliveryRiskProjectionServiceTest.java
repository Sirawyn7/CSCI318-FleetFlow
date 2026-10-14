package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlag;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlagRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(DeliveryRiskProjectionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryRiskProjectionServiceTest {
    @Autowired DeliveryRiskProjectionService service;
    @Autowired DeliveryRiskFlagRepository flags;
    @Autowired ProcessedEventRepository processed;

    private final UUID deliveryId = UUID.randomUUID();
    private final Instant t = Instant.parse("2026-10-14T03:00:00Z");

    private Instant raisedAt(String reason) {
        return flags.findById(new DeliveryRiskFlag.Key(deliveryId, reason)).orElseThrow().getRaisedAt();
    }

    @Test void keepsTheLatestTimePerReasonRegardlessOfArrivalOrder() {
        service.apply(UUID.randomUUID(), "DeliveryAtRisk", deliveryId, "STATIONARY", t);
        service.apply(UUID.randomUUID(), "DeliveryAtRisk", deliveryId, "STATIONARY", t.plusSeconds(600));
        service.apply(UUID.randomUUID(), "DeliveryAtRisk", deliveryId, "STATIONARY", t.plusSeconds(60));
        service.apply(UUID.randomUUID(), "DeliveryAtRisk", deliveryId, "SLA_BREACH", t);

        assertEquals(t.plusSeconds(600), raisedAt("STATIONARY"));
        assertEquals(t, raisedAt("SLA_BREACH"));
        assertEquals(2, flags.findByDeliveryId(deliveryId).size());
    }

    @Test void redeliveredEventIsIgnored() {
        UUID event = UUID.randomUUID();
        service.apply(event, "DeliveryAtRisk", deliveryId, "OFF_PACE", t);
        flags.deleteAll();
        service.apply(event, "DeliveryAtRisk", deliveryId, "OFF_PACE", t);

        assertTrue(flags.findByDeliveryId(deliveryId).isEmpty());
    }

    @Test void alertWithoutATimeIsAcknowledgedButNotRecorded() {
        UUID event = UUID.randomUUID();
        service.apply(event, "DeliveryAtRisk", deliveryId, "OFF_PACE", null);

        assertTrue(flags.findByDeliveryId(deliveryId).isEmpty());
        assertTrue(processed.existsById(event));
    }
}
