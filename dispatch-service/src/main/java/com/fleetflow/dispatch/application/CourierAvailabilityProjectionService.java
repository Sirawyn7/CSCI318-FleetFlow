package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Use-case orchestration and the transactional boundary. Takes plain UUID/String/entity
 * parameters rather than the contract record — contracts imports stay confined to
 * infrastructure/ (docs/decisions/0004, rule 4); the caller (the consumer bean) has
 * already translated the event before reaching here.
 */
@Service
public class CourierAvailabilityProjectionService {

    private final CourierAvailabilityViewRepository availabilityRepository;
    private final ProcessedEventRepository processedEventRepository;

    public CourierAvailabilityProjectionService(CourierAvailabilityViewRepository availabilityRepository,
                                                  ProcessedEventRepository processedEventRepository) {
        this.availabilityRepository = availabilityRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void apply(UUID eventId, String eventType, CourierAvailabilityView snapshot) {
        if (processedEventRepository.existsById(eventId)) {
            // Already applied -- redelivery of the same event, idempotent no-op.
            return;
        }
        availabilityRepository.save(snapshot);
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
