package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({AssignmentService.class, BoundedDispatchAgent.class, CourierLoadProjectionService.class,
        AssignmentPersistenceTest.Zones.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AssignmentPersistenceTest {
    @Autowired AssignmentService service;
    @Autowired CourierLoadProjectionService loadService;
    @Autowired CourierAvailabilityViewRepository couriers;
    @Autowired OpenOrderViewRepository orders;
    @Autowired AssignmentRepository assignments;
    @Autowired AssignmentDecisionRepository decisions;
    @Autowired ProcessedEventRepository processed;
    @MockitoBean DispatchAgent agent;
    @MockitoBean AssignmentEventPort events;

    @TestConfiguration
    static class Zones {
        @Bean OperatingZones zones() {
            var zones = new OperatingZones();
            zones.setZones(Map.of("ZONE_A", new OperatingZones.Bounds(-35, -34, 150, 151)));
            return zones;
        }
    }

    @BeforeEach void setup() {
        assignments.deleteAll(); decisions.deleteAll(); orders.deleteAll(); couriers.deleteAll(); processed.deleteAll();
        when(agent.recommend(any())).thenThrow(new IllegalStateException("Offline test"));
    }

    private UUID courier(int capacity) {
        var courier = new CourierAvailabilityView(UUID.randomUUID(), "VAN", capacity, "ZONE_A", true, Instant.now());
        courier.updateLocation(-34.4, 150.8, Instant.now());
        return couriers.save(courier).getCourierId();
    }
    private OpenOrderView order() {
        return orders.save(new OpenOrderView(UUID.randomUUID(), UUID.randomUUID(), -34.4, 150.8, -34.5, 150.9,
                "STANDARD", Instant.now().plusSeconds(3600), Instant.now()));
    }

    @Test void concurrentOrdersCannotReserveTheSameLastSlot() throws Exception {
        UUID courierId = courier(1);
        OpenOrderView first = order(), second = order();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Future<?> a = pool.submit(() -> { await(start); service.attemptAssignment(first); });
            Future<?> b = pool.submit(() -> { await(start); service.attemptAssignment(second); });
            start.countDown();
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, assignments.count());
        assertEquals(1, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        assertEquals(2, decisions.count());
        assertEquals(1, orders.count());
    }

    @Test void acknowledgementDoesNotDoubleCountAndCompletionReleasesCapacity() {
        UUID courierId = courier(1);
        OpenOrderView first = order();
        service.attemptAssignment(first);
        UUID acknowledgement = UUID.randomUUID();
        loadService.apply(acknowledgement, courierId, 1, 1, 1, first.getOrderId());
        loadService.apply(acknowledgement, courierId, 1, 1, 1, first.getOrderId());
        assertEquals(0, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        OpenOrderView blocked = order();
        service.attemptAssignment(blocked);
        assertEquals(DecisionOutcome.FAILED, decisions.findByOrderId(blocked.getOrderId()).orElseThrow().getOutcome());
        loadService.apply(UUID.randomUUID(), courierId, 0, 1, 2, first.getOrderId());
        // A delayed old assignment snapshot must not restore the completed load.
        loadService.apply(UUID.randomUUID(), courierId, 1, 1, 1, first.getOrderId());
        service.attemptAssignment(order());
        assertEquals(2, assignments.count());
        assertEquals(0, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
    }

    @Test void reorderedAcknowledgementsKeepUnacknowledgedReservationsConservative() {
        UUID courierId = courier(3);
        OpenOrderView first = order(), second = order();
        service.attemptAssignment(first); service.attemptAssignment(second);
        loadService.apply(UUID.randomUUID(), courierId, 2, 3, 2, second.getOrderId());
        assertEquals(1, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        loadService.apply(UUID.randomUUID(), courierId, 1, 3, 1, first.getOrderId());
        assertEquals(0, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        assertEquals(2, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
    }

    @Test void failedPublicationRollsBackDecisionReservationAndBacklogRemoval() {
        UUID courierId = courier(1);
        OpenOrderView order = order();
        doThrow(new IllegalStateException("broker down")).when(events).publishOrderAssigned(any());
        assertThrows(IllegalStateException.class, () -> service.attemptAssignment(order));
        assertEquals(0, assignments.count());
        assertEquals(0, decisions.count());
        assertTrue(orders.existsById(order.getOrderId()));
        assertEquals(0, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        doNothing().when(events).publishOrderAssigned(any());
        service.attemptAssignment(order);
        assertEquals(1, assignments.count());
    }

    @Test void agentPathPersistsLongJustificationAndDuplicateDeliveryDoesNotRepeatIt() {
        UUID courierId = courier(1);
        OpenOrderView order = order();
        String rationale = "Capacity and deadline trade-off. ".repeat(30);
        doReturn(new AgentRecommendation(order.getOrderId(), courierId, rationale)).when(agent).recommend(any());
        service.attemptAssignment(order); service.attemptAssignment(order);
        var decision = decisions.findByOrderId(order.getOrderId()).orElseThrow();
        assertEquals(AssignmentPath.AGENT, decision.getPathUsed());
        assertEquals(rationale, decision.getReason());
        assertEquals(1, assignments.count());
        verify(agent, times(1)).recommend(any());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
