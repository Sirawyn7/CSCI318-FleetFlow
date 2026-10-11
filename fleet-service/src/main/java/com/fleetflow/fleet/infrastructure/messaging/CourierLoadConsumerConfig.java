package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.order.OrderCancelled;
import com.fleetflow.contracts.tracking.DeliveryCompleted;
import com.fleetflow.fleet.application.CourierLoadService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.function.Consumer;

@Configuration
public class CourierLoadConsumerConfig {
    @Bean
    }
    @Bean
    public Consumer<com.fleetflow.contracts.dispatch.OrderReassigned> orderReassignedConsumer(CourierLoadService service) {
        return event -> service.reassign(event.envelope().eventId(), event.orderId(),
                event.previousCourierId(), event.newCourierId());
