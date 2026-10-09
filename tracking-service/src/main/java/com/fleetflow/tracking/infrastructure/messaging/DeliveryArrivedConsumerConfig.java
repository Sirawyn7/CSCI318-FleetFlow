package com.fleetflow.tracking.infrastructure.messaging;

import com.fleetflow.contracts.tracking.DeliveryArrived;
import com.fleetflow.tracking.application.DeliveryArrivalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.function.Consumer;

@Configuration
public class DeliveryArrivedConsumerConfig {
    @Bean
    public Consumer<DeliveryArrived> deliveryArrivedConsumer(DeliveryArrivalService service) {
        return event -> service.arrive(event.deliveryId(), event.arrivedAt());
    }
}
