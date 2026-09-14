package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.VehicleType;
import com.fleetflow.fleet.infrastructure.messaging.CourierRegisteredEventPublisher;
import com.fleetflow.fleet.infrastructure.persistence.CourierJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case orchestration and the transactional boundary (docs/AGENTS.md package rules).
 * Publishing happens inside the same transaction as the save: if the transaction rolls
 * back, StreamBridge.send has already fired (Spring Cloud Stream doesn't participate in
 * the JPA transaction) — acceptable here since registration has no other side effects
 * to roll back against, but worth noting for a reader comparing against stricter
 * outbox-pattern designs.
 */
@Service
public class CourierRegistrationService {

    private final CourierJpaRepository courierRepository;
    private final CourierRegisteredEventPublisher eventPublisher;

    public CourierRegistrationService(CourierJpaRepository courierRepository,
                                       CourierRegisteredEventPublisher eventPublisher) {
        this.courierRepository = courierRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Courier registerCourier(RegisterCourierCommand command) {
        if (command.vehicleType() == null) throw new IllegalArgumentException("vehicleType is required");
        VehicleType vehicleType = VehicleType.valueOf(command.vehicleType().toUpperCase(java.util.Locale.ROOT));
        Courier courier = Courier.register(vehicleType, command.capacity(), command.operatingZone());
        courierRepository.save(courier);
        eventPublisher.publish(courier);
        return courier;
    }
}
