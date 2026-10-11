package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentRepository;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.OrderCancellationState;
import com.fleetflow.dispatch.infrastructure.persistence.OrderCancellationStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Use-case orchestration and the transactional boundary for open_orders_view, reusing
 * the same processed_events ledger as {@link CourierAvailabilityProjectionService}
 * rather than a second idempotency table (docs/decisions/0001). Takes plain
 * UUID/String/entity parameters rather than contract records — contracts imports stay
 * confined to infrastructure/ (docs/decisions/0004, rule 4); the caller (the consumer
 * bean) has already translated the event before reaching here.
 */
@Service
public class OpenOrdersProjectionService {

    private final OpenOrderViewRepository openOrderViewRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final AssignmentRepository assignments;
    private final CourierAvailabilityViewRepository couriers;
    private final OrderCancellationStateRepository cancellations;

    public OpenOrdersProjectionService(OpenOrderViewRepository openOrderViewRepository,
                                        ProcessedEventRepository processedEventRepository,
                                        AssignmentRepository assignments,
                                        CourierAvailabilityViewRepository couriers,
                                        OrderCancellationStateRepository cancellations) {
        this.openOrderViewRepository = openOrderViewRepository;
        this.processedEventRepository = processedEventRepository;
        this.assignments = assignments;
        this.couriers = couriers;
        this.cancellations = cancellations;
    }

    @Transactional
    public void applyOrderPlaced(UUID eventId, String eventType, OpenOrderView snapshot) {
        if (processedEventRepository.existsById(eventId)) {
            // Already applied -- redelivery of the same event, idempotent no-op.
            return;
        }
        var state = lockCancellationState(snapshot.getOrderId());
        if (!state.isCancelled()) openOrderViewRepository.save(snapshot);
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }

    @Transactional
    public void applyOrderCancelled(UUID eventId, String eventType, UUID orderId) {
        if (processedEventRepository.existsById(eventId)) {
            // Already applied -- redelivery of the same event, idempotent no-op.
            return;
        }
        var state = lockCancellationState(orderId);
        state.cancel();
        // Same lock order as assignment commit: order, then courier. If assignment
        // is committing, wait before looking up the resulting reservation.
        if (openOrderViewRepository.findForUpdate(orderId).isPresent()) {
            openOrderViewRepository.deleteById(orderId);
        }
        assignments.findByOrderId(orderId).ifPresent(assignment -> {
            couriers.findForUpdate(assignment.getCourierId()).orElseThrow();
            assignment.cancel();
        });
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }

    private OrderCancellationState lockCancellationState(UUID orderId) {
        return cancellations.findForUpdate(orderId).orElseGet(() -> {
            var state = new OrderCancellationState(orderId);
            // An absent row cannot be locked. Competing first inserts conflict on the
            // primary key and Kafka retries the loser after the winner has committed.
            cancellations.saveAndFlush(state);
            return state;
        });
    }
}
