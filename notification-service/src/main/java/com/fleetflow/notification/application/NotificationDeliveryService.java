package com.fleetflow.notification.application;

import com.fleetflow.notification.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class NotificationDeliveryService {
    private final NotificationRepository notifications;
    private final DeliveryOrderLinkRepository links;
    private final NotificationTemplateRepository templates;
    private final DeliveryDataPort data;
    private final NotificationAuditPort audit;
    public NotificationDeliveryService(NotificationRepository notifications, DeliveryOrderLinkRepository links,
            NotificationTemplateRepository templates, DeliveryDataPort data, NotificationAuditPort audit) {
        this.notifications = notifications; this.links = links; this.templates = templates;
        this.data = data; this.audit = audit;
    }
    @Transactional
    public void deliver(UUID id) {
        var n = notifications.lockById(id).orElseThrow();
        if (n.getSentAt() != null) return;
        UUID orderId = n.getOrderId();
        if (orderId == null) orderId = links.findById(n.getDeliveryId()).map(DeliveryOrderLink::getOrderId).orElse(null);
        // Cross-topic order is not guaranteed: keep the durable pending message until its link arrives.
        if (orderId == null) return;
        var order = data.order(orderId);
        if (order == null || !orderId.equals(order.id()) || order.customerId() == null)
            throw new DeliveryDataPort.Unavailable("Order");
        var template = templates.findById(n.getKind()).orElseGet(() -> templates.save(new NotificationTemplate(n.getKind())));
        n.deliver(orderId, order.customerId(), template.render(orderId, n.getReason()));
    }
    @Transactional
    public void publishAudit(UUID id) {
        var n = notifications.lockById(id).orElseThrow();
        if (n.getSentAt() == null || n.isAuditPublished()) return;
        // Inbox was committed in a previous transaction. Audit is at-least-once with a stable ID.
        audit.publish(n);
        n.markAuditPublished();
    }
}
