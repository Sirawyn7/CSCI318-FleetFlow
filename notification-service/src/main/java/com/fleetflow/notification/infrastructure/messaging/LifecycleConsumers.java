package com.fleetflow.notification.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.tracking.*;
import com.fleetflow.notification.application.NotificationService;
import com.fleetflow.notification.domain.NotificationKind;
import org.springframework.context.annotation.*;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
public class LifecycleConsumers {
    private final NotificationService notifications;
    public LifecycleConsumers(NotificationService notifications) { this.notifications = notifications; }
    @Bean public Consumer<OrderAssigned> notifyAssigned() {
        return e -> receive(e.envelope(), e.orderId(), null, NotificationKind.ASSIGNED, null);
    }
    @Bean public Consumer<DeliveryStarted> notifyStarted() {
        return e -> receive(e.envelope(), e.orderId(), e.deliveryId(), NotificationKind.IN_TRANSIT, null);
    }
    @Bean public Consumer<DeliveryNearDestination> notifyNearDestination() {
        return e -> receive(e.envelope(), null, e.deliveryId(), NotificationKind.NEAR_DESTINATION, null);
    }
    @Bean public Consumer<DeliveryCompleted> notifyCompleted() {
        return e -> receive(e.envelope(), e.orderId(), e.deliveryId(), NotificationKind.DELIVERED, null);
    }
    @Bean public Consumer<DeliveryAtRisk> notifyAtRisk() {
        return e -> receive(e.envelope(), e.orderId(), e.deliveryId(), NotificationKind.AT_RISK, e.riskReason());
    }
    private void receive(EventEnvelope e, UUID order, UUID delivery, NotificationKind kind, String reason) {
        notifications.receive(e.eventId(), e.occurredAt(), e.correlationId(), order, delivery, kind, reason);
    }
}
