package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.*;
import com.fleetflow.fleet.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({CourierLoadService.class, CourierManagementService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CourierPersistenceTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.fleetflow.fleet.infrastructure.messaging.CourierLoadChangedEventPublisher publisher;
    @Autowired CourierJpaRepository couriers;
    @Autowired CourierOrderLoadRepository loads;
    @Autowired ProcessedEventRepository processed;
    @Autowired CourierLoadService service;
    @Autowired CourierManagementService management;

    private Courier courier(int capacity) { return couriers.save(Courier.register(VehicleType.VAN, capacity, "A")); }

    }

    @Test void reassignmentTransfersLoadOnceAndDelayedOriginalAssignmentCannotRestoreIt() {
        UUID old = courier(2).getId(), replacement = courier(2).getId(), order = UUID.randomUUID();
        service.apply(UUID.randomUUID(), order, old, false);
        UUID event = UUID.randomUUID();
        service.reassign(event, order, old, replacement);
        service.reassign(event, order, old, replacement);
        service.reassign(UUID.randomUUID(), order, old, replacement);
        service.apply(UUID.randomUUID(), order, old, false);
        assertEquals(0, management.get(old).getActiveOrderCount());
        assertEquals(1, management.get(replacement).getActiveOrderCount());
        service.apply(UUID.randomUUID(), order, replacement, true);
        assertEquals(0, management.get(replacement).getActiveOrderCount());
    }

    @Test void reassignmentBeforeOriginalAssignmentAndCompletionBeforeReassignmentAreSafe() {
        UUID old = courier(2).getId(), replacement = courier(2).getId(), order = UUID.randomUUID();
        service.reassign(UUID.randomUUID(), order, old, replacement);
        service.apply(UUID.randomUUID(), order, old, false);
        assertEquals(0, management.get(old).getActiveOrderCount());
        assertEquals(1, management.get(replacement).getActiveOrderCount());
        UUID completed = UUID.randomUUID();
        service.apply(UUID.randomUUID(), completed, replacement, true);
        service.reassign(UUID.randomUUID(), completed, old, replacement);
        service.apply(UUID.randomUUID(), completed, old, false);
        assertEquals(1, management.get(replacement).getActiveOrderCount());
    }

    @Test void oldCourierCompletionInFlightReleasesTheTransferredLoad() {
        UUID old = courier(2).getId(), replacement = courier(2).getId(), order = UUID.randomUUID();
        service.apply(UUID.randomUUID(), order, old, false);
        service.reassign(UUID.randomUUID(), order, old, replacement);
        service.apply(UUID.randomUUID(), order, old, true);
        service.apply(UUID.randomUUID(), order, old, true);
        assertEquals(0, management.get(old).getActiveOrderCount());
        assertEquals(0, management.get(replacement).getActiveOrderCount());
    }

    @Test void fullReplacementOrFailedPublicationRollsBackTransfer() {
        UUID old = courier(2).getId(), replacement = courier(1).getId(), order = UUID.randomUUID();
        service.apply(UUID.randomUUID(), order, old, false);
        UUID other = UUID.randomUUID(); service.apply(UUID.randomUUID(), other, replacement, false);
        UUID event = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> service.reassign(event, order, old, replacement));
        assertFalse(processed.existsById(event)); assertEquals(old, loads.findById(order).orElseThrow().getCourierId());
        service.apply(UUID.randomUUID(), other, replacement, true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Broker down")).when(publisher)
                .publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(order));
        assertThrows(IllegalStateException.class, () -> service.reassign(event, order, old, replacement));
        assertEquals(1, management.get(old).getActiveOrderCount());
        assertEquals(0, management.get(replacement).getActiveOrderCount());
        assertFalse(processed.existsById(event));
