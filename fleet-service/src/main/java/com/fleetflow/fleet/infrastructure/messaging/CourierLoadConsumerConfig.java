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
    public Consumer<OrderCancelled> orderCancelledConsumer(CourierLoadService service) {
        return event -> service.cancel(event.envelope().eventId(), event.orderId());
    }
    @Bean
    public Consumer<OrderAssigned> orderAssignedConsumer(CourierLoadService service) {
        return event -> service.apply(event.envelope().eventId(), event.orderId(), event.courierId(), false);
    }
    @Bean
    public Consumer<DeliveryCompleted> deliveryCompletedConsumer(CourierLoadService service) {
        return event -> service.apply(event.envelope().eventId(), event.orderId(), event.courierId(), true);
    }
}
