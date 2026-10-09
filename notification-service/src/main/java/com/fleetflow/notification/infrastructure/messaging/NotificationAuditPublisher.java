package com.fleetflow.notification.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.notification.NotificationSent;
import com.fleetflow.notification.application.NotificationAuditPort;
import com.fleetflow.notification.infrastructure.persistence.NotificationRecord;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
public class NotificationAuditPublisher implements NotificationAuditPort {
    private final StreamBridge bridge;
    public NotificationAuditPublisher(StreamBridge bridge) { this.bridge = bridge; }
    public void publish(NotificationRecord n) {
        var event = new NotificationSent(new EventEnvelope(n.getId(), "NotificationSent", n.getSentAt(), n.getCorrelationId()),
                n.getId(), n.getRecipientId(), "IN_APP", n.getKind().name());
        if (!bridge.send("notificationSent-out-0", event)) throw new IllegalStateException("Notification audit publish failed");
    }
}
