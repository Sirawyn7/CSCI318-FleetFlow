package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentDecision;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentDecisionRepository;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentPath;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentRepository;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.DecisionOutcome;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private CourierAvailabilityViewRepository courierAvailabilityViewRepository;

    @Mock
    private OpenOrderViewRepository openOrderViewRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentDecisionRepository assignmentDecisionRepository;

    @Mock
    private AssignmentEventPort eventPort;

    private OpenOrderView openOrder(UUID orderId) {
        return new OpenOrderView(orderId, UUID.randomUUID(), -34.4, 150.8, -34.5, 150.9,
                "STANDARD", Instant.now().plusSeconds(3600), Instant.now());
    }

    @Test
    void attemptAssignmentIsANoOpWhenADecisionAlreadyExistsForTheOrder() {
        AssignmentService service = new AssignmentService(courierAvailabilityViewRepository, openOrderViewRepository,
                assignmentRepository, assignmentDecisionRepository, eventPort);
        UUID orderId = UUID.randomUUID();
        when(assignmentDecisionRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(new AssignmentDecision(orderId, UUID.randomUUID(),
                        AssignmentPath.RULE_BASED, DecisionOutcome.ASSIGNED, null, Instant.now())));

        service.attemptAssignment(openOrder(orderId));

        verifyNoInteractions(courierAvailabilityViewRepository, openOrderViewRepository, assignmentRepository, eventPort);
    }

    @Test
    void assignsTheEligibleCourierPublishesAndRemovesTheOpenOrderRow() {
        AssignmentService service = new AssignmentService(courierAvailabilityViewRepository, openOrderViewRepository,
                assignmentRepository, assignmentDecisionRepository, eventPort);
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        OpenOrderView order = openOrder(orderId);
        CourierAvailabilityView courier = new CourierAvailabilityView(courierId, "VAN", 5, "ZONE_A", true, Instant.now());
        courier.updateLocation(-34.41, 150.81, Instant.now());

        when(assignmentDecisionRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(courierAvailabilityViewRepository.findByAvailableTrue()).thenReturn(List.of(courier));
        when(openOrderViewRepository.existsById(orderId)).thenReturn(true);

        service.attemptAssignment(order);

        ArgumentCaptor<Assignment> assignmentCaptor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(assignmentCaptor.capture());
        assertEquals(orderId, assignmentCaptor.getValue().getOrderId());
        assertEquals(courierId, assignmentCaptor.getValue().getCourierId());

        ArgumentCaptor<AssignmentDecision> decisionCaptor = ArgumentCaptor.forClass(AssignmentDecision.class);
        verify(assignmentDecisionRepository).save(decisionCaptor.capture());
        assertEquals(DecisionOutcome.ASSIGNED, decisionCaptor.getValue().getOutcome());
        assertEquals(courierId, decisionCaptor.getValue().getCourierId());

        verify(openOrderViewRepository).deleteById(orderId);
        verify(eventPort).publishOrderAssigned(assignmentCaptor.getValue());
        verify(eventPort, never()).publishAssignmentFailed(any(), any());
    }

    @Test
    void recordsAFailedDecisionAndLeavesTheOpenOrderRowWhenNoCourierIsEligible() {
        AssignmentService service = new AssignmentService(courierAvailabilityViewRepository, openOrderViewRepository,
                assignmentRepository, assignmentDecisionRepository, eventPort);
        UUID orderId = UUID.randomUUID();
        OpenOrderView order = openOrder(orderId);

        when(assignmentDecisionRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(courierAvailabilityViewRepository.findByAvailableTrue()).thenReturn(List.of());

        service.attemptAssignment(order);

        ArgumentCaptor<AssignmentDecision> decisionCaptor = ArgumentCaptor.forClass(AssignmentDecision.class);
        verify(assignmentDecisionRepository).save(decisionCaptor.capture());
        assertEquals(DecisionOutcome.FAILED, decisionCaptor.getValue().getOutcome());

        verify(assignmentRepository, never()).save(any());
        verify(openOrderViewRepository, never()).deleteById(any());
        verify(eventPort).publishAssignmentFailed(eq(orderId), any());
        verify(eventPort, never()).publishOrderAssigned(any());
    }
}
