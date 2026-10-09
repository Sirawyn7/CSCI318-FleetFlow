package com.fleetflow.notification.application;

import com.fleetflow.notification.domain.NotificationKind;
import com.fleetflow.notification.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationService {
    private final ProcessedEventRepository processed;
    private final NotificationRepository notifications;
    private final DeliveryOrderLinkRepository links;
    public NotificationService(ProcessedEventRepository processed, NotificationRepository notifications,
                               DeliveryOrderLinkRepository links) {
        this.processed = processed; this.notifications = notifications; this.links = links;
    }

    @Transactional
    public void receive(UUID eventId, Instant occurredAt, String correlationId, UUID orderId,
                        UUID deliveryId, NotificationKind kind, String reason) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(kind, "kind");
        if (orderId == null && deliveryId == null) throw new IllegalArgumentException("Missing order/delivery ID");
        if (reason != null && reason.length() > 2000) throw new IllegalArgumentException("Risk reason too long");
        if (correlationId != null && correlationId.length() > 1000) throw new IllegalArgumentException("Correlation ID too long");
        if (processed.existsById(eventId)) return;
        // Flush the unique claim before side effects; concurrent duplicates roll back and retry.
        processed.saveAndFlush(new ProcessedEvent(eventId));
        if (orderId != null && deliveryId != null) {
            var existing = links.findById(deliveryId);
            if (existing.isPresent() && !existing.get().getOrderId().equals(orderId))
                throw new IllegalArgumentException("Delivery mapped to a different order");
            links.save(new DeliveryOrderLink(deliveryId, orderId));
        }
        notifications.save(new NotificationRecord(eventId, orderId, deliveryId, kind, reason, occurredAt, correlationId));
    }

    @Transactional(readOnly = true)
    public List<InboxMessage> inbox(UUID customerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size (1-100)");
        return notifications.findByRecipientIdAndSentAtIsNotNullOrderByOccurredAtDesc(customerId, PageRequest.of(page, size))
                .stream().map(n -> new InboxMessage(n.getId(), n.getOrderId(), n.getKind(), n.getBody(), n.getOccurredAt(), n.getSentAt())).toList();
    }
    public record InboxMessage(UUID id, UUID orderId, NotificationKind kind, String message, Instant occurredAt, Instant sentAt) { }
}
