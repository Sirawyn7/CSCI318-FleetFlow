package com.fleetflow.order.infrastructure;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.dispatch.AssignmentFailed;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.tracking.DeliveryCompleted;
import com.fleetflow.contracts.tracking.DeliveryStarted;
import com.fleetflow.order.application.OrderApplicationService;
import com.fleetflow.order.domain.OrderStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/** FR2: lifecycle events owned by Dispatch and Tracking advance the order's status history. */
@Configuration
public class OrderLifecycleConsumers {

    private final OrderApplicationService orders;

    public OrderLifecycleConsumers(OrderApplicationService orders) {
        this.orders = orders;
    }

    @Bean
    public Consumer<OrderAssigned> orderAssignedConsumer() {
        return event -> record(event.orderId(), OrderStatus.ASSIGNED, event.envelope());
    }

    @Bean
    public Consumer<DeliveryStarted> deliveryStartedConsumer() {
        return event -> record(event.orderId(), OrderStatus.IN_TRANSIT, event.envelope());
    }

    @Bean
    public Consumer<DeliveryCompleted> deliveryCompletedConsumer() {
        return event -> record(event.orderId(), OrderStatus.DELIVERED, event.envelope());
    }

    @Bean
    public Consumer<AssignmentFailed> assignmentFailedConsumer() {
        return event -> record(event.orderId(), OrderStatus.FAILED, event.envelope());
    }

    private void record(UUID orderId, OrderStatus status, EventEnvelope envelope) {
        // The source event's time, not receipt time, so retries don't shift the history.
        Instant at = envelope.occurredAt() == null ? Instant.now() : envelope.occurredAt();
        orders.recordLifecycle(orderId, status, at);
    }
}
