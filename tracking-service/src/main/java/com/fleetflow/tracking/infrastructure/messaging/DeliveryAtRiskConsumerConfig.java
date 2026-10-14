package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.tracking.DeliveryAtRisk;
import com.fleetflow.tracking.application.DeliveryRiskProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/** Tracking consuming its own risk alerts to build a REST-queryable risk view (FR16). */
@Configuration
public class DeliveryAtRiskConsumerConfig {

    @Bean
    public Consumer<DeliveryAtRisk> deliveryRiskProjectionConsumer(DeliveryRiskProjectionService service) {
        return event -> service.apply(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.deliveryId(),
                event.riskReason(),
                event.envelope().occurredAt());
    }
}
