package com.fleetflow.notification.infrastructure.persistence;

import com.fleetflow.notification.domain.NotificationKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** Durable inbox plus audit outbox. The source event ID is the deduplication identity. */
@Entity
@Table(name = "notifications")
public class NotificationRecord {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID sourceEventId;
    private UUID orderId;
    private UUID deliveryId;
    private UUID recipientId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 64) private NotificationKind kind;
    @Column(length = 2000) private String reason;
    @Column(length = 4000) private String body;
    @Column(length = 1000) private String correlationId;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant sentAt;
    private boolean auditPublished;
    @Version private long version;
    protected NotificationRecord() { }
    public NotificationRecord(UUID eventId, UUID orderId, UUID deliveryId, NotificationKind kind,
                              String reason, Instant occurredAt, String correlationId) {
        this.id = UUID.randomUUID(); this.sourceEventId = eventId; this.orderId = orderId;
        this.deliveryId = deliveryId; this.kind = kind; this.reason = reason;
        this.occurredAt = occurredAt; this.correlationId = correlationId; this.createdAt = Instant.now();
    }
    public void deliver(UUID orderId, UUID recipientId, String body) {
        if (sentAt != null) return;
        this.orderId = orderId; this.recipientId = recipientId; this.body = body; this.sentAt = Instant.now();
    }
    public void markAuditPublished() { auditPublished = true; }
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getRecipientId() { return recipientId; }
    public NotificationKind getKind() { return kind; }
    public String getReason() { return reason; }
    public String getBody() { return body; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public boolean isAuditPublished() { return auditPublished; }
}
