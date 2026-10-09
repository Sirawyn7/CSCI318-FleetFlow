package com.fleetflow.notification.infrastructure.messaging;

import com.fleetflow.notification.application.NotificationDeliveryService;
import com.fleetflow.notification.infrastructure.persistence.NotificationRecord;
import com.fleetflow.notification.infrastructure.persistence.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notification.worker.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationWorker.class);
    private final NotificationRepository notifications;
    private final NotificationDeliveryService delivery;
    private Cursor pendingCursor;
    private Cursor auditCursor;
    public NotificationWorker(NotificationRepository notifications, NotificationDeliveryService delivery) {
        this.notifications = notifications; this.delivery = delivery;
    }
    @Scheduled(fixedDelayString = "${notification.worker.poll-ms:5000}")
    public void run() {
        var pending = notifications.findNextPending(
                pendingCursor == null ? null : pendingCursor.timestamp(),
                pendingCursor == null ? null : pendingCursor.id(), PageRequest.of(0, 25));
        pending.forEach(n -> attempt(n.getId(), () -> delivery.deliver(n.getId())));
        pendingCursor = nextCursor(pending, pending.size() == 25);

        var audits = notifications.findNextAudit(
                auditCursor == null ? null : auditCursor.timestamp(),
                auditCursor == null ? null : auditCursor.id(), PageRequest.of(0, 25));
        audits.forEach(n -> attempt(n.getId(), () -> delivery.publishAudit(n.getId())));
        auditCursor = nextCursor(audits, audits.size() == 25);
    }
    private Cursor nextCursor(List<NotificationRecord> records, boolean hasNextPage) {
        if (!hasNextPage || records.isEmpty()) return null;
        var last = records.getLast();
        return new Cursor(last.getSentAt() == null ? last.getCreatedAt() : last.getSentAt(), last.getId());
    }
    private record Cursor(Instant timestamp, UUID id) { }
    private void attempt(UUID id, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException ex) { LOG.warn("Notification {} remains pending ({})", id, ex.getClass().getSimpleName()); }
    }
}
