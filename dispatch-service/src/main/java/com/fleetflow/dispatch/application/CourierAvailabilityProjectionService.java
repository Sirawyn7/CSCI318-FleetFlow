package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
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
        // Registration is not a profile update. A redelivery with a new envelope
        // must not reset an existing courier's load, location or availability.
        if (availabilityRepository.findById(snapshot.getCourierId()).isEmpty()) {
            availabilityRepository.save(snapshot);
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }

    @Transactional
    public void applyAvailabilityChange(UUID eventId, String eventType, UUID courierId, boolean available, Instant updateAt) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        Optional<CourierAvailabilityView> existing = availabilityRepository.findForUpdate(courierId);
        if (existing.isEmpty()) {
            throw new IllegalStateException("Availability received before courier registration");
        }

        CourierAvailabilityView view = existing.get();
        if (view.getAvailabilityUpdatedAt() == null || !updateAt.isBefore(view.getAvailabilityUpdatedAt())) {
            view.markAvailability(available, updateAt);
        }
        availabilityRepository.save(view);
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }

    @Transactional
    public void applyLocationUpdate(UUID eventId, String eventType, UUID courierId, double latitude,
                                     double longitude, Instant capturedAt) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        Optional<CourierAvailabilityView> existing = availabilityRepository.findForUpdate(courierId);
        if (existing.isEmpty()) {
            // Throw so the binder actually retries; returning would acknowledge and
            // permanently lose the update despite not recording it as processed.
            throw new IllegalStateException("Location received before courier registration");
        }

        CourierAvailabilityView view = existing.get();
        if (view.getLocationUpdatedAt() == null || !capturedAt.isBefore(view.getLocationUpdatedAt())) {
            view.updateLocation(latitude, longitude, capturedAt);
        }
        availabilityRepository.save(view);
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
