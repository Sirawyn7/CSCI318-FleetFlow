package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.order.OrderCancelled;
import com.fleetflow.tracking.application.DeliveryCancellationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class OrderCancelledConsumerConfig {

    @Bean
    public Consumer<OrderCancelled> orderCancelledConsumer(DeliveryCancellationService cancellations) {
        return event -> cancellations.cancel(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.orderId(),
                event.envelope().occurredAt());
    }
}
