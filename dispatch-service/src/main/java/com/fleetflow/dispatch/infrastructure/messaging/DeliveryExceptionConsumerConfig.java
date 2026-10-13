package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.tracking.*;
import com.fleetflow.dispatch.application.DeliveryExceptionService;
import org.springframework.context.annotation.*;
import java.util.function.Consumer;

@Configuration
public class DeliveryExceptionConsumerConfig {
    @Bean Consumer<DeliveryStarted> deliveryStartedConsumer(DeliveryExceptionService service) {
        return e -> service.started(e.orderId(), e.deliveryId(), e.courierId());
    }
    @Bean Consumer<DeliveryAtRisk> deliveryAtRiskConsumer(DeliveryExceptionService service) {
        return e -> service.risk(e.orderId(), e.deliveryId(), e.courierId(), e.riskReason(), e.envelope().occurredAt());
    }
    @Bean Consumer<DeliveryCompleted> deliveryCompletedConsumer(DeliveryExceptionService service) {
        return e -> service.closed(e.orderId(), e.deliveryId());
    }
    @Bean Consumer<DeliveryArrived> deliveryArrivedConsumer(DeliveryExceptionService service) {
        return e -> service.arrived(e.deliveryId());
    }
}
