package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaView;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Projects Tracking's own DeliveryEtaUpdated events into the queryable eta view **/
@Service
public class DeliveryEtaProjectionService {

    private final DeliveryEtaViewRepository etaRepository;
    private final ProcessedEventRepository processedEventRepository;

    public DeliveryEtaProjectionService(DeliveryEtaViewRepository etaRepository,
                                        ProcessedEventRepository processedEventRepository) {
        this.etaRepository = etaRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void apply(UUID eventId, String eventType, UUID deliveryId, Instant estimatedArrival,
                      Instant occuredAt) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        etaRepository.findById(deliveryId).ifPresentOrElse(
                view -> view.replaceIfNewer(estimatedArrival, occuredAt),
                () -> etaRepository.save(new DeliveryEtaView(deliveryId, estimatedArrival, occuredAt)));
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
