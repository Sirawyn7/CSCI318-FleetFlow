package com.fleetflow.notification.infrastructure.messaging;

import com.fleetflow.notification.application.NotificationDeliveryService;
import com.fleetflow.notification.infrastructure.persistence.NotificationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notification.worker.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationWorker.class);
    private final NotificationRepository notifications;
    private final NotificationDeliveryService delivery;
    private int pendingPage;
    private int auditPage;
    public NotificationWorker(NotificationRepository notifications, NotificationDeliveryService delivery) {
        this.notifications = notifications; this.delivery = delivery;
    }
    @Scheduled(fixedDelayString = "${notification.worker.poll-ms:5000}")
    public void run() {
        // Rotate pages so unresolved deliveries cannot starve later messages.
        var pending = notifications.findBySentAtIsNullOrderByCreatedAtAsc(PageRequest.of(pendingPage, 25));
        pendingPage = pending.size() < 25 ? 0 : pendingPage + 1;
        pending.forEach(n -> attempt(n.getId(), () -> delivery.deliver(n.getId())));
        var audits = notifications.findBySentAtIsNotNullAndAuditPublishedFalseOrderBySentAtAsc(PageRequest.of(auditPage, 25));
        auditPage = audits.size() < 25 ? 0 : auditPage + 1;
        audits.forEach(n -> attempt(n.getId(), () -> delivery.publishAudit(n.getId())));
    }
    private void attempt(UUID id, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException ex) { LOG.warn("Notification {} remains pending ({})", id, ex.getClass().getSimpleName()); }
    }
}
