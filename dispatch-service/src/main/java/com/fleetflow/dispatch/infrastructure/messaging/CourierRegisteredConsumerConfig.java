package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.dispatch.application.CourierAvailabilityProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Functional consumer bean. Binding name convention: <bean-name>-in-0 (see
 * application.yml, spring.cloud.function.definition + spring.cloud.stream.bindings).
 * Consumer group = service name, so every dispatch-service instance shares one logical
 * subscription rather than each receiving its own copy of every event.
 */
@Configuration
public class CourierRegisteredConsumerConfig {

    @Bean
    public Consumer<CourierRegistered> courierRegisteredConsumer(
            CourierRegisteredTranslator translator,
            CourierAvailabilityProjectionService projectionService) {
        return event -> projectionService.apply(
                event.envelope().eventId(),
                event.envelope().eventType(),
                translator.translate(event));
    }
}
