package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryStartedEventPublisher;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeView;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationView;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.OrderCancellationState;
import com.fleetflow.tracking.infrastructure.persistence.OrderCancellationStateRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Use-case orchestration for FR11's prerequisite bootstrap step. On OrderAssigned,
 * looks up the order's destination and the courier's vehicle type from the two derived
 * read models, creates the Delivery aggregate, and publishes both DeliveryStarted (the
 * catalogue event) and the internal active-delivery snapshot the Streams topology
 * consumes.
 *
 * Missing prerequisites throw so the binder retries; after retry exhaustion, the
 * assignment is available in the DLQ for replay. A successful return would acknowledge
 * it permanently, regardless of whether processed_events was written.
 */
@Service
public class DeliveryBootstrapService {

    private final VehicleSpeedConfiguration speeds;
    private final OrderDestinationViewRepository destinationRepository;
    private final CourierVehicleTypeViewRepository vehicleTypeRepository;
    private final DeliveryJpaRepository deliveryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DeliveryStartedEventPublisher deliveryStartedPublisher;
    private final ActiveDeliverySnapshotPublisher activeDeliverySnapshotPublisher;
    private final OrderCancellationStateRepository cancellations;

    public DeliveryBootstrapService(OrderDestinationViewRepository destinationRepository,
                                    CourierVehicleTypeViewRepository vehicleTypeRepository,
                                    DeliveryJpaRepository deliveryRepository,
                                    ProcessedEventRepository processedEventRepository,
                                    DeliveryStartedEventPublisher deliveryStartedPublisher,
                                    ActiveDeliverySnapshotPublisher activeDeliverySnapshotPublisher, VehicleSpeedConfiguration speeds,
                                    OrderCancellationStateRepository cancellations) {
        this.cancellations = cancellations;
        this.speeds = speeds;
        this.destinationRepository = destinationRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.deliveryRepository = deliveryRepository;
        this.processedEventRepository = processedEventRepository;
        this.deliveryStartedPublisher = deliveryStartedPublisher;
        this.activeDeliverySnapshotPublisher = activeDeliverySnapshotPublisher;
    }

    @Transactional
    public void bootstrap(UUID eventId, String eventType, UUID orderId, UUID courierId) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        // Lock order: cancellation state, then courier, then delivery (as DeliveryCancellationService).
        // An absent row can't be locked, so racing first inserts conflict and Kafka retries one.
        var state = cancellations.findForUpdate(orderId).orElseGet(() -> {
            var created = new OrderCancellationState(orderId);
            cancellations.saveAndFlush(created);
            return created;
        });
        if (state.isCancelled()) {
            // FR3: no delivery, no DeliveryStarted, so the customer gets no "in transit" alert.
            processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
            return;
        }

        Optional<OrderDestinationView> destination = destinationRepository.findById(orderId);
        Optional<CourierVehicleTypeView> vehicleType = vehicleTypeRepository.findForUpdate(courierId);
        // Serialize snapshots for this courier, and recheck retries after acquiring the lock.
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        if (destination.isEmpty() || vehicleType.isEmpty()) {
            throw new IllegalStateException("Assignment prerequisites not projected yet; retry or replay the DLQ");
        }

        double averageSpeedKmh = speeds.kmhFor(vehicleType.get().getVehicleType());
        Delivery delivery = Delivery.start(orderId, courierId, destination.get().getDropoffLocation(),
                averageSpeedKmh, destination.get().getRequestedDeliveryBy());
        deliveryRepository.save(delivery);

        // Publish the internal projection first so its failure cannot leak DeliveryStarted.
        activeDeliverySnapshotPublisher.publish(delivery);
        deliveryStartedPublisher.publish(delivery);

        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}
