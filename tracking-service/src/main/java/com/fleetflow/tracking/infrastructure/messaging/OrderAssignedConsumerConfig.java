package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.tracking.application.DeliveryBootstrapService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class OrderAssignedConsumerConfig {

    @Bean
    public Consumer<OrderAssigned> orderAssignedConsumer(DeliveryBootstrapService bootstrapService) {
        return event -> bootstrapService.bootstrap(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.orderId(),
                event.courierId());
    }
}