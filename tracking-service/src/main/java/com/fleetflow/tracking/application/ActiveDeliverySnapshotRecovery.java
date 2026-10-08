package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Rebuild internal snapshots on restart without holding fleet-wide locks. */
@Service
public class ActiveDeliverySnapshotRecovery {
    private static final Logger log = LoggerFactory.getLogger(ActiveDeliverySnapshotRecovery.class);
    private final DeliveryJpaRepository deliveries;
    private final CourierVehicleTypeViewRepository couriers;
    private final ActiveDeliverySnapshotPublisher publisher;
    private final TransactionTemplate transaction;

    public ActiveDeliverySnapshotRecovery(DeliveryJpaRepository deliveries,
            CourierVehicleTypeViewRepository couriers, ActiveDeliverySnapshotPublisher publisher,
            PlatformTransactionManager transactionManager) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.publisher = publisher;
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (var id : deliveries.findCourierIds()) {
            try {
                // Commit/release this courier's lock before attempting the next courier.
                transaction.executeWithoutResult(status -> {
                    if (couriers.findForUpdate(id).isEmpty()) {
                        log.warn("Skipping snapshot recovery: missing courier projection {}", id);
                        return;
                    }
                    publisher.publish(id);
                });
            } catch (RuntimeException failure) {
                log.error("Snapshot recovery failed for courier {}; repair and retry recovery", id, failure);
            }
        }
    }
}
