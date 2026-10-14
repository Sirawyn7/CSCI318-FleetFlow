package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlag;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlagRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Projects Tracking's own DeliveryAtRisk events into the queryable risk flags (FR16). */
@Service
public class DeliveryRiskProjectionService {

    private final DeliveryRiskFlagRepository flags;
    private final ProcessedEventRepository processed;

    public DeliveryRiskProjectionService(DeliveryRiskFlagRepository flags, ProcessedEventRepository processed) {
        this.flags = flags;
        this.processed = processed;
    }

    @Transactional
    public void apply(UUID eventId, String eventType, UUID deliveryId, String reason, Instant occurredAt) {
        if (processed.existsById(eventId)) return;
        // Without a time or reason the alert can't be ordered or reported; record it as handled.
        if (deliveryId != null && reason != null && !reason.isBlank() && occurredAt != null) {
            flags.findById(new DeliveryRiskFlag.Key(deliveryId, reason)).ifPresentOrElse(
                    flag -> flag.raiseIfNewer(occurredAt),
                    () -> flags.save(new DeliveryRiskFlag(deliveryId, reason, occurredAt)));
        }
        processed.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
