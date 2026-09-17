package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.fleet.CourierLoadChanged;
import com.fleetflow.dispatch.application.CourierLoadProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.function.Consumer;

@Configuration
public class CourierLoadChangedConsumerConfig {
    @Bean
    public Consumer<CourierLoadChanged> courierLoadChangedConsumer(CourierLoadProjectionService service) {
        return event -> service.apply(event.envelope().eventId(), event.courierId(), event.activeOrderCount(),
                event.capacityCeiling(), event.loadVersion(), event.acknowledgedOrderId());
    }
}
