package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.VehicleAverageSpeed;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryStartedEventPublisher;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeView;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationView;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationViewRepository;
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
 * Idempotency mirrors the fix applied to FR5's CourierAvailabilityProjectionService
 * (see that service's Javadoc): OrderAssigned can arrive before OrderPlaced or
 * CourierRegistered have been projected here, since Kafka doesn't guarantee ordering
 * across different topics. When either lookup is missing, this deliberately returns
 * WITHOUT recording a ProcessedEvent, so a later redelivery can still bootstrap the
 * delivery once both read models are populated, rather than the assignment being
 * silently and permanently dropped.
 */
@Service
public class DeliveryBootstrapService {

    private final OrderDestinationViewRepository destinationRepository;
    private final CourierVehicleTypeViewRepository vehicleTypeRepository;
    private final DeliveryJpaRepository deliveryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DeliveryStartedEventPublisher deliveryStartedPublisher;
    private final ActiveDeliverySnapshotPublisher activeDeliverySnapshotPublisher;

    public DeliveryBootstrapService(OrderDestinationViewRepository destinationRepository,
                                    CourierVehicleTypeViewRepository vehicleTypeRepository,
                                    DeliveryJpaRepository deliveryRepository,
                                    ProcessedEventRepository processedEventRepository,
                                    DeliveryStartedEventPublisher deliveryStartedPublisher,
                                    ActiveDeliverySnapshotPublisher activeDeliverySnapshotPublisher) {
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

        Optional<OrderDestinationView> destination = destinationRepository.findById(orderId);
        Optional<CourierVehicleTypeView> vehicleType = vehicleTypeRepository.findById(courierId);
        if (destination.isEmpty() || vehicleType.isEmpty()) {
            return;
        }

        double averageSpeedKmh = VehicleAverageSpeed.kmhFor(vehicleType.get().getVehicleType());
        Delivery delivery = Delivery.start(orderId, courierId, destination.get().getDropoffLocation(),
                averageSpeedKmh);
        deliveryRepository.save(delivery);

        deliveryStartedPublisher.publish(delivery);
        activeDeliverySnapshotPublisher.publish(delivery);

        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}