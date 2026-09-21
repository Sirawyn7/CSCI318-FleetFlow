package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeView;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEvent;
import com.fleetflow.tracking.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CourierVehicleTypeProjectionService {

    private final CourierVehicleTypeViewRepository vehicleTypeRepository;
    private final ProcessedEventRepository processedEventRepository;

    public CourierVehicleTypeProjectionService(CourierVehicleTypeViewRepository vehicleTypeRepository,
                                               ProcessedEventRepository processedEventRepository) {
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void apply(UUID eventId, String eventType, UUID courierId, String vehicleType) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        vehicleTypeRepository.save(new CourierVehicleTypeView(courierId, vehicleType));
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }
}