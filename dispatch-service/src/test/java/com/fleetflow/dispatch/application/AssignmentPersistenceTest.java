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
@Import({AssignmentService.class, BoundedDispatchAgent.class,
        CourierLoadProjectionService.class, CourierAvailabilityProjectionService.class,
        AssignmentPersistenceTest.Zones.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AssignmentPersistenceTest {
    @Autowired AssignmentService service;
    @Autowired CourierLoadProjectionService loadService;
    @Autowired CourierAvailabilityProjectionService availabilityService;
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

    @Test void slowAgentDoesNotBlockFleetUpdatesOrAnotherOrderAndItsChoiceIsRevalidated() throws Exception {
        UUID firstCourier = courier(1), otherCourier = courier(1);
        OpenOrderView slow = order(), fast = order();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            DispatchContext context = invocation.getArgument(0);
            if (context.targetOrderId().equals(slow.getOrderId())) {
                entered.countDown();
                assertTrue(release.await(4, TimeUnit.SECONDS));
                return new AgentRecommendation(slow.getOrderId(), firstCourier, "Originally had a free slot");
            }
            return new AgentRecommendation(fast.getOrderId(), otherCourier, "Other courier is eligible");
        }).when(agent).recommend(any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> waiting = pool.submit(() -> service.attemptAssignment(slow));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                Future<?> progress = pool.submit(() -> {
                    availabilityService.applyLocationUpdate(UUID.randomUUID(), "CourierLocationUpdated", firstCourier,
                            -34.45, 150.8, Instant.now());
                    loadService.apply(UUID.randomUUID(), "CourierLoadChanged", firstCourier, 1, 1, 1, UUID.randomUUID());
                    availabilityService.applyAvailabilityChange(UUID.randomUUID(), "CourierAvailabilityChanged", firstCourier,
                            false, Instant.now());
                    service.attemptAssignment(fast);
                });
                progress.get(2, TimeUnit.SECONDS);
                assertEquals(AssignmentPath.AGENT, decisions.findByOrderId(fast.getOrderId()).orElseThrow().getPathUsed());
                assertFalse(waiting.isDone());
            } finally { release.countDown(); }
            waiting.get(3, TimeUnit.SECONDS);
        }
        var stale = decisions.findByOrderId(slow.getOrderId()).orElseThrow();
        assertEquals(DecisionOutcome.FAILED, stale.getOutcome());
        assertTrue(stale.getReason().contains("became ineligible"));
        assertEquals(1, assignments.count());
    }

    @Test void cancellationDuringAgentCallPreventsCommit() throws Exception {
        UUID courierId = courier(1);
        OpenOrderView order = order();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(4, TimeUnit.SECONDS));
            return new AgentRecommendation(order.getOrderId(), courierId, "Snapshot was eligible");
        }).when(agent).recommend(any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> waiting = pool.submit(() -> service.attemptAssignment(order));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                pool.submit(() -> orders.deleteById(order.getOrderId())).get(2, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            waiting.get(3, TimeUnit.SECONDS);
        }
        assertEquals(0, assignments.count());
        assertEquals(0, decisions.count());
        verifyNoInteractions(events);
    }

    @Test void orphanAcknowledgementKeepsAuthoritativeLoadAndProcessesSubsequentUpdates() {
        UUID courierId = courier(2), historicalOrder = UUID.randomUUID(), eventId = UUID.randomUUID();
        loadService.apply(eventId, "CourierLoadChanged", courierId, 1, 2, 1, historicalOrder);
        assertTrue(processed.existsById(eventId));
        assertEquals(1, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
        loadService.apply(UUID.randomUUID(), "CourierLoadChanged", courierId, 0, 2, 2, historicalOrder);
        assertEquals(0, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
        assertEquals(0, assignments.count());
    }

    @Test void acknowledgementArrivingDuringPublicationWaitsForAssignmentCommit() throws Exception {
        UUID courierId = courier(2), eventId = UUID.randomUUID();
        OpenOrderView order = order();
        var publishing = new CountDownLatch(1);
        var releaseCommit = new CountDownLatch(1);
        var acknowledgementStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            publishing.countDown();
            assertTrue(releaseCommit.await(5, TimeUnit.SECONDS));
            return null;
        }).when(events).publishOrderAssigned(any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> assignment = pool.submit(() -> service.attemptAssignment(order));
            Future<?> acknowledgement;
            try {
                assertTrue(publishing.await(3, TimeUnit.SECONDS));
                // A separate transaction cannot yet see the reservation.
                assertTrue(assignments.findByOrderId(order.getOrderId()).isEmpty());
                acknowledgement = pool.submit(() -> {
                    acknowledgementStarted.countDown();
                    loadService.apply(eventId, "CourierLoadChanged", courierId, 1, 2, 1, order.getOrderId());
                });
                assertTrue(acknowledgementStarted.await(1, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> acknowledgement.get(200, TimeUnit.MILLISECONDS));
                assertFalse(processed.existsById(eventId));
            } finally { releaseCommit.countDown(); }
            assignment.get(5, TimeUnit.SECONDS);
            acknowledgement.get(5, TimeUnit.SECONDS);
        }
        assertTrue(processed.existsById(eventId));
        assertEquals(0, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        assertEquals(1, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
        // The acknowledged reservation must not consume the courier's second slot.
        service.attemptAssignment(order());
        assertEquals(2, assignments.count());
    }

    @Test void negativeVersionIsRejectedBeforeStalenessAndIsNotAcknowledged() {
        UUID courierId = courier(2), eventId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> loadService.apply(eventId, "CourierLoadChanged", courierId, 1, 2, -1, null));
        assertFalse(processed.existsById(eventId));
        assertEquals(0, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
        loadService.apply(UUID.randomUUID(), "CourierLoadChanged", courierId, 1, 2, 2, null);
        assertThrows(IllegalArgumentException.class,
                () -> loadService.apply(eventId, "CourierLoadChanged", courierId, -1, 2, 1, null));
        assertFalse(processed.existsById(eventId));
    }

    @Test void concurrentRegistrationsDoNotReplaceTheExistingProjection() throws Exception {
        UUID courierId = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    await(start);
                    availabilityService.apply(UUID.randomUUID(), "CourierRegistered",
                            new CourierAvailabilityView(courierId, "VAN", 2, "ZONE_A", false, Instant.now()));
                }));
            }
            start.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, couriers.count());
        assertEquals(4, processed.count());
        loadService.apply(UUID.randomUUID(), "CourierLoadChanged", courierId, 1, 2, 1, null);
        availabilityService.apply(UUID.randomUUID(), "CourierRegistered",
                new CourierAvailabilityView(courierId, "BIKE", 9, "OTHER", false, Instant.now()));
        var existing = couriers.findById(courierId).orElseThrow();
        assertEquals(1, existing.getActiveOrderCount());
        assertEquals(2, existing.getCapacity());
        assertEquals("VAN", existing.getVehicleType());
    }

    @Test void acknowledgementDoesNotDoubleCountAndCompletionReleasesCapacity() {
        UUID courierId = courier(1);
        OpenOrderView first = order();
        service.attemptAssignment(first);
        UUID acknowledgement = UUID.randomUUID();
        loadService.apply(acknowledgement, "CourierLoadChanged", courierId, 1, 1, 1, first.getOrderId());
        loadService.apply(acknowledgement, "CourierLoadChanged", courierId, 1, 1, 1, first.getOrderId());
        assertEquals(0, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        OpenOrderView blocked = order();
        service.attemptAssignment(blocked);
        assertEquals(DecisionOutcome.FAILED, decisions.findByOrderId(blocked.getOrderId()).orElseThrow().getOutcome());
        loadService.apply(UUID.randomUUID(), "CourierLoadChanged", courierId, 0, 1, 2, first.getOrderId());
        // A delayed old assignment snapshot must not restore the completed load.
        loadService.apply(UUID.randomUUID(), "CourierLoadChanged", courierId, 1, 1, 1, first.getOrderId());
        service.attemptAssignment(order());
        assertEquals(2, assignments.count());
        assertEquals(0, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
    }

    @Test void reorderedAcknowledgementsKeepUnacknowledgedReservationsConservative() {
        UUID courierId = courier(3);
        OpenOrderView first = order(), second = order();
        service.attemptAssignment(first); service.attemptAssignment(second);
        loadService.apply(UUID.randomUUID(), "CourierLoadChanged", courierId, 2, 3, 2, second.getOrderId());
        assertEquals(1, assignments.countByCourierIdAndLoadAcknowledgedFalse(courierId));
        loadService.apply(UUID.randomUUID(), "CourierLoadChanged", courierId, 1, 3, 1, first.getOrderId());
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
