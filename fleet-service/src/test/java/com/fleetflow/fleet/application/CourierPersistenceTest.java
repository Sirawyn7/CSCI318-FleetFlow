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

    @Test void duplicatesAndCompletionPersistExactlyOnce() {
        UUID id = courier(2).getId(), order = UUID.randomUUID(), event = UUID.randomUUID();
        service.apply(event, order, id, false);
        service.apply(event, order, id, false);
        service.apply(UUID.randomUUID(), order, id, false);
        assertEquals(1, management.get(id).getActiveOrderCount());
        UUID completion = UUID.randomUUID();
        service.apply(completion, order, id, true);
        service.apply(completion, order, id, true);
        service.apply(UUID.randomUUID(), order, id, true);
        assertEquals(0, management.get(id).getActiveOrderCount());
        assertEquals(2, management.get(id).getLoadVersion());
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(4)).publish(
                org.mockito.ArgumentMatchers.any(Courier.class), org.mockito.ArgumentMatchers.eq(order));
    }

    @Test void failedLoadPublicationRollsBackCounterAndEventLedger() {
        UUID id = courier(2).getId(), order = UUID.randomUUID(), event = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalStateException("Broker down")).when(publisher)
                .publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(order));
        assertThrows(IllegalStateException.class, () -> service.apply(event, order, id, false));
        assertEquals(0, management.get(id).getActiveOrderCount());
        assertEquals(0, management.get(id).getLoadVersion());
        assertFalse(processed.existsById(event));
        assertFalse(loads.existsById(order));
    }

    @Test void completionBeforeAssignmentDoesNotConsumeAnotherOrdersLoad() {
        UUID id = courier(2).getId(), first = UUID.randomUUID(), second = UUID.randomUUID();
        service.apply(UUID.randomUUID(), first, id, false);
        service.apply(UUID.randomUUID(), second, id, true);
        service.apply(UUID.randomUUID(), second, id, false);
        assertEquals(1, management.get(id).getActiveOrderCount());
    }

    @Test void rejectedAssignmentRollsBackLedgerAndCanBeRetried() {
        UUID id = courier(1).getId(), first = UUID.randomUUID(), second = UUID.randomUUID();
        service.apply(UUID.randomUUID(), first, id, false);
        UUID rejectedEvent = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> service.apply(rejectedEvent, second, id, false));
        assertFalse(processed.existsById(rejectedEvent));
        assertFalse(loads.existsById(second));
        service.apply(UUID.randomUUID(), first, id, true);
        service.apply(rejectedEvent, second, id, false);
        assertEquals(1, management.get(id).getActiveOrderCount());
    }

    @Test void profileAndDeactivationArePersisted() {
        UUID id = courier(2).getId();
        management.update(id, new RegisterCourierCommand("van", 4, "B"));
        assertEquals(4, management.get(id).getCapacity());
        management.deactivate(id);
        assertTrue(management.get(id).isDeactivated());
        assertThrows(CourierNotFoundException.class, () -> management.get(UUID.randomUUID()));
    }
}
