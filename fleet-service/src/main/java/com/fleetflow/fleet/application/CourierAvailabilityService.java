package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.infrastructure.messaging.CourierAvailabilityChangedEventPublisher;
import com.fleetflow.fleet.infrastructure.persistence.CourierJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class CourierAvailabilityService {
    private final CourierJpaRepository courierRepository;
    private final CourierAvailabilityChangedEventPublisher eventPublisher;

    public CourierAvailabilityService(CourierJpaRepository courierRepository, CourierAvailabilityChangedEventPublisher eventPublisher) {
        this.courierRepository = courierRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Courier changeAvailability(ChangeCourierAvailabilityCommand command) {
        Courier courier = courierRepository.findById(command.courierId()).orElseThrow(() -> new NoSuchElementException("courier not found" +  command.courierId()));

        courier.changeAvailability(command.available());
        courierRepository.save(courier);
        eventPublisher.publish(courier);
        return courier;
    }
}
