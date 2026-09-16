package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.dispatch.application.CourierAvailabilityProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Functional consumer bean for FR10's CourierLocationUpdated, same shape as
 * CourierAvailabilityChangedConsumerConfig -- no dedicated Translator class, since
 * this is a direct field extraction with no shape translation to do. Feeds
 * courier_availability_view's location fields, the prerequisite FR7's proximity
 * ranking needs (docs/decisions/0001, deferred to FR7/FR8).
 */
@Configuration
public class CourierLocationUpdatedConsumerConfig {

    @Bean
    public Consumer<CourierLocationUpdated> courierLocationUpdatedConsumer(CourierAvailabilityProjectionService projectionService) {
        return event -> projectionService.applyLocationUpdate(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.courierId(),
                event.location().latitude(),
                event.location().longitude(),
                event.capturedAt()
        );
    }
}
