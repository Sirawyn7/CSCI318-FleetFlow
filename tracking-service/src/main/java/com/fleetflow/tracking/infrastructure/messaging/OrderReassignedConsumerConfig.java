package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.dispatch.OrderReassigned;
import com.fleetflow.tracking.application.DeliveryReassignmentService;
import org.springframework.context.annotation.*;
import java.util.function.Consumer;

@Configuration
public class OrderReassignedConsumerConfig {
    @Bean Consumer<OrderReassigned> orderReassignedConsumer(DeliveryReassignmentService service) {
        return e -> service.reassign(e.envelope().eventId(), e.orderId(), e.previousCourierId(), e.newCourierId());
    }
}
