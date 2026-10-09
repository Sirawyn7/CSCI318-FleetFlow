package com.fleetflow.notification.application;

import com.fleetflow.notification.infrastructure.persistence.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class NotificationDeliveryService {
    private final NotificationRepository notifications;
    private final DeliveryOrderLinkRepository links;
    private final NotificationTemplateRepository templates;
    private final DeliveryDataPort data;
    private final NotificationAuditPort audit;
    private final TransactionTemplate writeTransaction;
    public NotificationDeliveryService(NotificationRepository notifications, DeliveryOrderLinkRepository links,
            NotificationTemplateRepository templates, DeliveryDataPort data, NotificationAuditPort audit,
            PlatformTransactionManager transactionManager) {
        this.notifications = notifications; this.links = links; this.templates = templates;
        this.data = data; this.audit = audit;
        writeTransaction = new TransactionTemplate(transactionManager);
        writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deliver(UUID id) {
        var n = notifications.findById(id).orElseThrow();
        if (n.getSentAt() != null) return;
        UUID orderId = n.getOrderId();
        if (orderId == null) orderId = links.findById(n.getDeliveryId()).map(DeliveryOrderLink::getOrderId).orElse(null);
        // Cross-topic order is not guaranteed: keep the durable pending message until its link arrives.
        if (orderId == null) return;
        var order = data.order(orderId);
        if (order == null || !orderId.equals(order.id()) || order.customerId() == null)
            throw new DeliveryDataPort.Unavailable("Order");
        UUID finalOrderId = orderId;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                writeTransaction.executeWithoutResult(status -> {
                    var current = notifications.lockById(id).orElseThrow();
                    if (current.getSentAt() != null) return;
                    var template = templates.findById(current.getKind())
                            .orElseGet(() -> templates.saveAndFlush(new NotificationTemplate(current.getKind())));
                    current.deliver(finalOrderId, order.customerId(),
                            template.render(finalOrderId, current.getReason()));
                });
                return;
            } catch (DataIntegrityViolationException conflict) {
                if (attempt == 1) throw conflict;
            }
        }
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishAudit(UUID id) {
        var n = notifications.findById(id).orElseThrow();
        if (n.getSentAt() == null || n.isAuditPublished()) return;
        audit.publish(n);
        writeTransaction.executeWithoutResult(status -> {
            var current = notifications.lockById(id).orElseThrow();
            if (current.getSentAt() != null && !current.isAuditPublished()) current.markAuditPublished();
        });
    }
}
