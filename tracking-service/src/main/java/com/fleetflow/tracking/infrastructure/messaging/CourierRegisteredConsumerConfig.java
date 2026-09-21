package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.tracking.application.CourierVehicleTypeProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Tracking's own consumer of CourierRegistered -- a separate projection from
 * Dispatch's own consumer of the same event (each service keeps its own derived read
 * model, per the project's event-driven convention; no service queries another's
 * database or REST API for this).
 */
@Configuration
public class CourierRegisteredConsumerConfig {

    @Bean
    public Consumer<CourierRegistered> courierRegisteredConsumer(
            CourierVehicleTypeProjectionService projectionService) {
        return event -> projectionService.apply(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.courierId(),
                event.vehicleType());
    }
}