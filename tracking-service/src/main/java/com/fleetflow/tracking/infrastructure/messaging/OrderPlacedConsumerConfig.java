package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.tracking.application.OrderDestinationProjectionService;
import com.fleetflow.tracking.domain.GeoCoordinate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class OrderPlacedConsumerConfig {

    @Bean
    public Consumer<OrderPlaced> orderPlacedConsumer(OrderDestinationProjectionService projectionService) {
        return event -> projectionService.apply(
                event.envelope().eventId(),
                event.envelope().eventType(),
                event.orderId(),
                new GeoCoordinate(event.dropoffLocation().latitude(), event.dropoffLocation().longitude()),
                event.requestedDeliveryBy());
    }
}