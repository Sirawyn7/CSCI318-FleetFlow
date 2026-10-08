package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.tracking.DeliveryEtaUpdated;
import com.fleetflow.tracking.application.DeliveryEtaProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/** Tracking consuming its own event to build a REST-queryable ETA view (ADR 0014). */
@Configuration
public class DeliveryEtaUpdatedConsumerConfig {

    @Bean
    public Consumer<DeliveryEtaUpdated> deliveryEtaUpdatedConsumer(DeliveryEtaProjectionService service) {
        return event -> service.apply(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.deliveryId(),
                event.estimatedArrival(),
                event.envelope().occurredAt());
    }
}
