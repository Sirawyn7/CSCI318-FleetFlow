package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.domain.CourierCandidate;
import com.fleetflow.dispatch.domain.DispatchAssignmentService;
import com.fleetflow.dispatch.domain.GeoCoordinate;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentDecision;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentDecisionRepository;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentPath;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentRepository;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.DecisionOutcome;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * FR7's use-case orchestration: on OrderPlaced (see OpenOrdersConsumerConfig),
 * attempts a rule-based assignment for one order. Takes plain infrastructure/domain
 * types rather than contract records -- contracts imports stay confined to
 * infrastructure/ (docs/decisions/0004 rule 4); the caller (the consumer bean) has
 * already translated the event before reaching here.
 */
@Service
public class AssignmentService {

    private static final String NO_ELIGIBLE_COURIER_REASON = "No available courier with known location and capacity";

    private final CourierAvailabilityViewRepository courierAvailabilityViewRepository;
    private final OpenOrderViewRepository openOrderViewRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentDecisionRepository assignmentDecisionRepository;
    private final AssignmentEventPort eventPort;
    private final DispatchAssignmentService dispatchAssignmentService = new DispatchAssignmentService();

    public AssignmentService(CourierAvailabilityViewRepository courierAvailabilityViewRepository,
                              OpenOrderViewRepository openOrderViewRepository,
                              AssignmentRepository assignmentRepository,
                              AssignmentDecisionRepository assignmentDecisionRepository,
                              AssignmentEventPort eventPort) {
        this.courierAvailabilityViewRepository = courierAvailabilityViewRepository;
        this.openOrderViewRepository = openOrderViewRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentDecisionRepository = assignmentDecisionRepository;
        this.eventPort = eventPort;
    }

    @Transactional
    public void attemptAssignment(OpenOrderView order) {
        // assignment_decisions, keyed by orderId, is this concern's own idempotency
        // ledger -- deliberately not the shared processed_events table, which is
        // already marked processed for this same OrderPlaced event by
        // OpenOrdersProjectionService.applyOrderPlaced. Checking that table here would
        // always read "already processed" and skip assignment on the very first
        // delivery, not just on redelivery.
        if (assignmentDecisionRepository.findByOrderId(order.getOrderId()).isPresent()) {
            return;
        }

        List<CourierCandidate> candidates = courierAvailabilityViewRepository.findByAvailableTrue().stream()
                .filter(view -> view.getLatitude() != null && view.getLongitude() != null)
                .map(this::toCandidate)
                .toList();

        GeoCoordinate pickup = new GeoCoordinate(order.getPickupLatitude(), order.getPickupLongitude());
        Optional<CourierCandidate> selected = dispatchAssignmentService.select(pickup, candidates);

        // One clock read shared by the Assignment and its AssignmentDecision -- they
        // record the same decision and should carry the same timestamp, not two
        // separate Instant.now() calls that could drift apart.
        Instant decidedAt = Instant.now();

        if (selected.isPresent()) {
            assignSuccessfully(order, selected.get(), decidedAt);
        } else {
            recordFailure(order, decidedAt);
        }
    }

    private void assignSuccessfully(OpenOrderView order, CourierCandidate courier, Instant decidedAt) {
        Assignment assignment = Assignment.create(order.getOrderId(), courier.courierId(), decidedAt);
        assignmentRepository.save(assignment);
        assignmentDecisionRepository.save(new AssignmentDecision(
                order.getOrderId(), courier.courierId(), AssignmentPath.RULE_BASED, DecisionOutcome.ASSIGNED,
                null, decidedAt));

        // This is ADR 0001's deferred piece: an assigned order leaves the backlog.
        // Guarded, matching OpenOrdersProjectionService.applyOrderCancelled's existing
        // convention, rather than calling deleteById unconditionally.
        if (openOrderViewRepository.existsById(order.getOrderId())) {
            openOrderViewRepository.deleteById(order.getOrderId());
        }

        eventPort.publishOrderAssigned(assignment);
    }

    private void recordFailure(OpenOrderView order, Instant decidedAt) {
        assignmentDecisionRepository.save(new AssignmentDecision(
                order.getOrderId(), null, AssignmentPath.RULE_BASED, DecisionOutcome.FAILED,
                NO_ELIGIBLE_COURIER_REASON, decidedAt));
        // open_orders_view keeps the row -- the order stays in the backlog. This is a
        // terminal outcome for FR7's scope: the idempotency check above means this
        // order is never reconsidered automatically. Closing that gap is FR8 or a
        // future reassignment-style sweep, not this slice.
        eventPort.publishAssignmentFailed(order.getOrderId(), NO_ELIGIBLE_COURIER_REASON);
    }

    private CourierCandidate toCandidate(CourierAvailabilityView view) {
        return new CourierCandidate(
                view.getCourierId(),
                view.getCapacity(),
                new GeoCoordinate(view.getLatitude(), view.getLongitude()));
    }
}
