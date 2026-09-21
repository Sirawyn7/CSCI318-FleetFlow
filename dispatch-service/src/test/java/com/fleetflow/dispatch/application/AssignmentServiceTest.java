package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {
    @Mock CourierAvailabilityViewRepository couriers;
    @Mock OpenOrderViewRepository orders;
    @Mock AssignmentRepository assignments;
    @Mock AssignmentDecisionRepository decisions;
    @Mock AssignmentEventPort events;
    @Mock BoundedDispatchAgent agent;
    AssignmentService service;
    OpenOrderView order;
    CourierAvailabilityView near;
    CourierAvailabilityView far;

    @BeforeEach void setup() {
        OperatingZones zones = new OperatingZones();
        zones.setZones(Map.of("ZONE_A", new OperatingZones.Bounds(-35, -34, 150, 151)));
        service = new AssignmentService(couriers, orders, assignments, decisions, agent, zones, events,
                mock(org.springframework.transaction.PlatformTransactionManager.class));
        order = new OpenOrderView(UUID.randomUUID(), UUID.randomUUID(), -34.4, 150.8, -34.5, 150.9,
                "EXPRESS", Instant.now().plusSeconds(600), Instant.now());
        near = courier(-34.41, "ZONE_A", 3);
        far = courier(-34.6, "ZONE_A", 3);
    }

    private CourierAvailabilityView courier(double latitude, String zone, int capacity) {
        var courier = new CourierAvailabilityView(UUID.randomUUID(), "VAN", capacity, zone, true, Instant.now());
        courier.updateLocation(latitude, 150.8, Instant.now());
        return courier;
    }
    private void prepare(List<CourierAvailabilityView> fleet, AgentAttempt attempt) {
        when(orders.findById(order.getOrderId())).thenReturn(Optional.of(order));
        when(orders.findForUpdate(order.getOrderId())).thenReturn(Optional.of(order));
        when(orders.findAll()).thenReturn(List.of(order));
        when(couriers.findByAvailableTrue()).thenReturn(fleet);
        for (var courier : fleet) {
            lenient().when(couriers.findForUpdate(courier.getCourierId())).thenReturn(Optional.of(courier));
        }
        when(agent.attempt(any())).thenReturn(attempt);
    }
    private AssignmentDecision decision() {
        var captor = ArgumentCaptor.forClass(AssignmentDecision.class);
        verify(decisions).save(captor.capture());
        return captor.getValue();
    }

    @Test void duplicateIsANoOp() {
        when(decisions.findByOrderId(order.getOrderId())).thenReturn(Optional.of(new AssignmentDecision(
                order.getOrderId(), near.getCourierId(), AssignmentPath.AGENT, DecisionOutcome.ASSIGNED,
                "existing", Instant.now())));
        service.attemptAssignment(order);
        verifyNoInteractions(couriers, orders, assignments, events, agent);
    }

    @Test void removedOrderCannotBeAssigned() {
        service.attemptAssignment(order);
        verifyNoInteractions(couriers, assignments, events, agent);
        verify(decisions, never()).save(any());
    }

    @Test void validAgentChoiceTakesPrecedenceOverNearestCourierAndPersistsJustification() {
        String reason = "Preserve the nearer courier for the urgent backlog order.";
        prepare(List.of(near, far), new AgentAttempt(new AgentRecommendation(order.getOrderId(), far.getCourierId(), reason), null));
        service.attemptAssignment(order);
        assertEquals(AssignmentPath.AGENT, decision().getPathUsed());
        assertEquals(far.getCourierId(), decision().getCourierId());
        assertEquals(reason, decision().getReason());
        verify(events).publishOrderAssigned(any());
        verify(orders).deleteById(order.getOrderId());
        var context = ArgumentCaptor.forClass(DispatchContext.class);
        verify(agent).attempt(context.capture());
        assertEquals(order.getRequestedDeliveryBy(), context.getValue().openOrders().getFirst().deliveryDeadline());
    }

    @Test void timeoutFallsBackToNearestEligibleCourier() {
        prepare(List.of(far, near), new AgentAttempt(null, "Agent timed out"));
        service.attemptAssignment(order);
        assertEquals(AssignmentPath.RULE_BASED, decision().getPathUsed());
        assertEquals(near.getCourierId(), decision().getCourierId());
        assertTrue(decision().getReason().contains("timed out"));
    }

    @Test void inventedCourierIsRejected() {
        prepare(List.of(near), new AgentAttempt(new AgentRecommendation(order.getOrderId(), UUID.randomUUID(), "invented"), null));
        service.attemptAssignment(order);
        assertEquals(AssignmentPath.RULE_BASED, decision().getPathUsed());
        assertEquals(near.getCourierId(), decision().getCourierId());
    }

    @Test void newlyFullAgentChoiceFallsBackToAnotherCourier() {
        prepare(List.of(near, far), new AgentAttempt(
                new AgentRecommendation(order.getOrderId(), near.getCourierId(), "Was eligible"), null));
        when(couriers.findForUpdate(near.getCourierId())).thenAnswer(invocation -> {
            near.updateLoad(3, 3, 1);
            return Optional.of(near);
        });
        service.attemptAssignment(order);
        assertEquals(far.getCourierId(), decision().getCourierId());
        assertEquals(AssignmentPath.RULE_BASED, decision().getPathUsed());
        assertTrue(decision().getReason().contains("became ineligible"));
    }

    @Test void newlyUnavailableAgentChoiceFallsBackToAnotherCourier() {
        prepare(List.of(near, far), new AgentAttempt(
                new AgentRecommendation(order.getOrderId(), near.getCourierId(), "Was available"), null));
        when(couriers.findForUpdate(near.getCourierId())).thenAnswer(invocation -> {
            near.markAvailability(false, Instant.now());
            return Optional.of(near);
        });
        service.attemptAssignment(order);
        assertEquals(far.getCourierId(), decision().getCourierId());
        assertEquals(AssignmentPath.RULE_BASED, decision().getPathUsed());
    }

    @Test void wrongOrderIsRejected() {
        prepare(List.of(near), new AgentAttempt(new AgentRecommendation(UUID.randomUUID(), near.getCourierId(), "wrong order"), null));
        service.attemptAssignment(order);
        assertEquals(AssignmentPath.RULE_BASED, decision().getPathUsed());
    }

    @Test void blankJustificationIsRejected() {
        prepare(List.of(near), new AgentAttempt(new AgentRecommendation(order.getOrderId(), near.getCourierId(), " "), null));
        service.attemptAssignment(order);
        assertEquals(AssignmentPath.RULE_BASED, decision().getPathUsed());
    }

    @Test void wrongZoneAndFullCouriersAreRejectedOnBothPaths() {
        near.updateLoad(3, 3, 1);
        var wrongZone = courier(-34.4, "UNKNOWN", 3);
        prepare(List.of(near, wrongZone, far), new AgentAttempt(
                new AgentRecommendation(order.getOrderId(), near.getCourierId(), "Full but nearest"), null));
        service.attemptAssignment(order);
        assertEquals(far.getCourierId(), decision().getCourierId());
        assertEquals(AssignmentPath.RULE_BASED, decision().getPathUsed());
    }

    @Test void pendingReservationsConsumeRemainingCapacity() {
        near.updateLoad(2, 3, 1);
        when(assignments.countByCourierIdAndLoadAcknowledgedFalse(near.getCourierId())).thenReturn(1);
        prepare(List.of(near, far), new AgentAttempt(null, "Agent unavailable"));
        service.attemptAssignment(order);
        assertEquals(far.getCourierId(), decision().getCourierId());
    }

    @Test void noEligibleCourierRecordsFailureAndKeepsBacklog() {
        var unlocated = new CourierAvailabilityView(UUID.randomUUID(), "VAN", 3, "ZONE_A", true, Instant.now());
        prepare(List.of(unlocated), new AgentAttempt(null, "Agent unavailable"));
        service.attemptAssignment(order);
        assertEquals(DecisionOutcome.FAILED, decision().getOutcome());
        verify(events).publishAssignmentFailed(eq(order.getOrderId()), any());
        verify(assignments, never()).save(any());
        verify(orders, never()).deleteById(any());
    }
}
