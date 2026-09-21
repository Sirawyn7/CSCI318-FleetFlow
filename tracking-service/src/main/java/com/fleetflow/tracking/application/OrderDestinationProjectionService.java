package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationView;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderDestinationProjectionService {

    private final OrderDestinationViewRepository destinationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public OrderDestinationProjectionService(OrderDestinationViewRepository destinationRepository,
                                             ProcessedEventRepository processedEventRepository) {
        this.destinationRepository = destinationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void apply(UUID eventId, String eventType, UUID orderId, GeoCoordinate dropoffLocation,
                      Instant requestedDeliveryBy) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        destinationRepository.save(new OrderDestinationView(orderId, dropoffLocation, requestedDeliveryBy));
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}