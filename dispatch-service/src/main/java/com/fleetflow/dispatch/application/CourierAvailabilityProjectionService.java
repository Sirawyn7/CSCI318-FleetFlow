package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

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
    private final TransactionTemplate registrationTransaction;

    public CourierAvailabilityProjectionService(CourierAvailabilityViewRepository availabilityRepository,
                                                  ProcessedEventRepository processedEventRepository,
                                                  PlatformTransactionManager transactionManager) {
        this.availabilityRepository = availabilityRepository;
        this.processedEventRepository = processedEventRepository;
        registrationTransaction = new TransactionTemplate(transactionManager);
        registrationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void apply(UUID eventId, String eventType, CourierAvailabilityView snapshot) {
        // An absent row cannot be protected by a row lock. Competing first inserts
        // are resolved by the primary key, then retried once in a fresh transaction.
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                registrationTransaction.executeWithoutResult(status -> register(eventId, eventType, snapshot));
                return;
            } catch (DataIntegrityViolationException conflict) {
                if (attempt == 1) throw conflict;
            }
        }
    }

    private void register(UUID eventId, String eventType, CourierAvailabilityView snapshot) {
        if (processedEventRepository.existsById(eventId)) return;
        var existing = availabilityRepository.findForUpdate(snapshot.getCourierId());
        if (processedEventRepository.existsById(eventId)) return;
        // Never merge a detached registration over a courier changed by other events.
        if (existing.isEmpty()) availabilityRepository.insertRegistration(snapshot.getCourierId(),
                snapshot.getVehicleType(), snapshot.getCapacity(), snapshot.getOperatingZone(),
                snapshot.isAvailable(), snapshot.getUpdatedAt());
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
