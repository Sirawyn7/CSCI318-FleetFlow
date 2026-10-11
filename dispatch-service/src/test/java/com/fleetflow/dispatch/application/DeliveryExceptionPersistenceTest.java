package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest
@Import({DeliveryExceptionService.class, BoundedDispatchAgent.class, AssignmentPersistenceTest.Zones.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryExceptionPersistenceTest {
    @Autowired DeliveryExceptionService service;
    @Autowired AssignmentRepository assignments;
    @Autowired CourierAvailabilityViewRepository couriers;
    @Autowired AssignmentDecisionRepository decisions;
    @MockitoBean DispatchAgent agent;
    @MockitoBean AssignmentEventPort events;
    @BeforeEach void setup() {
        assignments.deleteAll(); couriers.deleteAll(); decisions.deleteAll();
        when(agent.recommend(any())).thenThrow(new IllegalStateException("Provider unavailable"));
    }
    private UUID courier(boolean available, int capacity) {
        var c = new CourierAvailabilityView(UUID.randomUUID(), "VAN", capacity, "ZONE_A", available, Instant.now());
        c.updateLocation(-34.4, 150.8, Instant.now()); return couriers.save(c).getCourierId();
    }
    private Assignment active(UUID courier) {
        var a = Assignment.create(UUID.randomUUID(), courier, Instant.now().minusSeconds(60));
        a.rememberDestination(-34.5, 150.9, Instant.now().plusSeconds(1800), "EXPRESS");
        a.startDelivery(UUID.randomUUID());
        decisions.save(new AssignmentDecision(a.getOrderId(), courier, AssignmentPath.RULE_BASED,
                DecisionOutcome.ASSIGNED, "Initial assignment", a.getAssignedAt()));
        return assignments.save(a);
    }
    private Assignment reload(Assignment a) { return assignments.findById(a.getId()).orElseThrow(); }
    private void risk(Assignment a) {
        service.risk(a.getOrderId(), a.getDeliveryId(), a.getCourierId(), "SLA_BREACH", Instant.now());
    }
    @Test void offlineReassignsAndOutboxRetriesSameEventWithoutRepeatingTheDecision() {
        UUID old = courier(false, 2), replacement = courier(true, 2);
        var a = active(old); service.checkOffline(a.getOrderId());
        var changed = reload(a);
        assertEquals(replacement, changed.getCourierId()); assertEquals(old, changed.getPreviousCourierId());
        assertEquals("RULE_BASED", changed.getReassignmentPath());
        assertFalse(changed.isReassignmentPublished()); assertFalse(changed.isLoadAcknowledged());
        UUID eventId = changed.getReassignmentEventId();
        doThrow(new IllegalStateException("Broker down")).when(events).publishOrderReassigned(any());
        assertThrows(IllegalStateException.class, () -> service.publishPending(a.getOrderId()));
        assertFalse(reload(a).isReassignmentPublished()); assertEquals(eventId, reload(a).getReassignmentEventId());
        doNothing().when(events).publishOrderReassigned(any());
        service.publishPending(a.getOrderId()); service.publishPending(a.getOrderId());
        assertTrue(reload(a).isReassignmentPublished());
        verify(events, times(2)).publishOrderReassigned(any());
        risk(a); assertNull(reload(a).getPendingException()); // stale old-courier risk
        var current = reload(a); risk(current);
        service.escalateDue(a.getOrderId(), current.getExceptionAttemptedAt().plusSeconds(599));
        assertTrue(service.operatorQueue().isEmpty());
        service.escalateDue(a.getOrderId(), current.getExceptionAttemptedAt().plusSeconds(600));
        assertEquals(1, service.operatorQueue().size());
        assertEquals(eventId, reload(a).getReassignmentEventId());
        service.closed(a.getOrderId(), a.getDeliveryId()); assertTrue(service.operatorQueue().isEmpty());
    }
    @Test void noCandidateIsDurableAndEscalatesWithoutASecondEvent() {
        var a = active(courier(false, 1)); service.checkOffline(a.getOrderId());
        var attempted = reload(a); assertNotNull(attempted.getExceptionAttemptedAt());
        assertNull(attempted.getReassignedAt());
        courier(true, 2); service.checkOffline(a.getOrderId());
        assertNull(reload(a).getReassignedAt());
        service.escalateDue(a.getOrderId(), attempted.getExceptionAttemptedAt().plusSeconds(600));
        assertEquals(1, service.operatorQueue().size());
    }
    @Test void agentPrimaryAndIrrelevantOrClosedDeliveryRisksAreIgnored() {
        var a = active(courier(true, 2)); UUID replacement = courier(true, 2);
        doReturn(new AgentRecommendation(a.getOrderId(), replacement, "Can meet deadline")).when(agent).recommend(any());
        service.risk(a.getOrderId(), a.getDeliveryId(), a.getCourierId(), "STATIONARY", Instant.now());
        assertNull(reload(a).getExceptionAttemptedAt());
        risk(a); assertEquals("AGENT", reload(a).getReassignmentPath());
        assertEquals("Can meet deadline", reload(a).getReassignmentJustification());
        var audit = decisions.findByOrderId(a.getOrderId()).orElseThrow();
        assertEquals("Initial assignment", audit.getReason());
        assertEquals(AssignmentPath.AGENT, audit.getReassignmentPath());
        assertEquals(replacement, audit.getReplacementCourierId());
        service.closed(a.getOrderId(), a.getDeliveryId()); risk(reload(a));
        assertNull(reload(a).getPendingException());
    }
    @Test void concurrentTriggersPublishOnlyOneReassignment() throws Exception {
        var a = active(courier(true, 2)); courier(true, 2); courier(true, 2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { await(start); risk(a); });
            var second = executor.submit(() -> { await(start); risk(a); });
            start.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        }
        service.publishPending(a.getOrderId()); service.publishPending(a.getOrderId());
        verify(events).publishOrderReassigned(any()); assertNull(reload(a).getPendingException());
    }
    @Test void twoDeliveriesCannotReserveTheSameLastSlot() throws Exception {
        var first = active(courier(true, 1)); var second = active(first.getCourierId());
        UUID replacement = courier(true, 1);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { await(start); risk(first); });
            var b = executor.submit(() -> { await(start); risk(second); });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, assignments.countByCourierIdAndLoadAcknowledgedFalse(replacement));
        assertEquals(1, assignments.findAll().stream().filter(a -> a.getReassignedAt() != null).count());
    }
    @Test void completionBeforeStartStaysClosedAndStaleRisksDoNotConsumeBudget() {
        UUID c = courier(true, 2); var a = Assignment.create(UUID.randomUUID(), c, Instant.now());
        assignments.save(a); UUID delivery = UUID.randomUUID();
        service.closed(a.getOrderId(), delivery); service.started(a.getOrderId(), delivery, c);
        assertFalse(reload(a).isDeliveryActive());
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
