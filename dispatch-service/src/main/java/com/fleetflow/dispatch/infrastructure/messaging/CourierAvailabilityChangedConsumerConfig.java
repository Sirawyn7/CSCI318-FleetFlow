package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierAvailabilityChanged;
import com.fleetflow.dispatch.application.CourierAvailabilityProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Functional consumer bean for FR5's CourierAvailabilityChanged, same shape as
 * CourierRegisteredConsumerConfig. Extracts plain fields here (courierId/available/
 * timestamp) rather than a separate Translator class -- there's no shape translation
 * to do, just unpacking the envelope, so a dedicated ACL class would be a pass-through.
 */

@Configuration
public class CourierAvailabilityChangedConsumerConfig {
    @Bean
    public Consumer<CourierAvailabilityChanged> courierAvailabilityChangedConsumer(CourierAvailabilityProjectionService projectionService) {
        return event -> projectionService.applyAvailabilityChange(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.courierId(),
                event.available(),
                event.envelope().occurredAt()
        );
    }
}
