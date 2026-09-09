package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
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

    public OpenOrdersProjectionService(OpenOrderViewRepository openOrderViewRepository,
                                        ProcessedEventRepository processedEventRepository) {
        this.openOrderViewRepository = openOrderViewRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void applyOrderPlaced(UUID eventId, String eventType, OpenOrderView snapshot) {
        if (processedEventRepository.existsById(eventId)) {
            // Already applied -- redelivery of the same event, idempotent no-op.
            return;
        }
        openOrderViewRepository.save(snapshot);
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }

    @Transactional
    public void applyOrderCancelled(UUID eventId, String eventType, UUID orderId) {
        if (processedEventRepository.existsById(eventId)) {
            // Already applied -- redelivery of the same event, idempotent no-op.
            return;
        }
        // deleteById would throw if the row is already absent (e.g. OrderCancelled
        // arriving for an order this view never saw an OrderPlaced for) -- guard with
        // existsById so removal is a safe no-op rather than an error.
        if (openOrderViewRepository.existsById(orderId)) {
            openOrderViewRepository.deleteById(orderId);
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
