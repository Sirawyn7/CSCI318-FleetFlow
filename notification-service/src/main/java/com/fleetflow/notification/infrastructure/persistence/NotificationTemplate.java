package com.fleetflow.notification.infrastructure.persistence;

import com.fleetflow.notification.domain.NotificationKind;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    @Id @Enumerated(EnumType.STRING) private NotificationKind code;
    @Column(nullable = false, length = 2000) private String body;
    protected NotificationTemplate() { }
    public NotificationTemplate(NotificationKind code) { this.code = code; this.body = code.defaultText(); }
    public String render(java.util.UUID orderId, String reason) {
        return body.replace("{orderId}", orderId.toString())
                .replace("{reason}", reason == null || reason.isBlank() ? "No reason supplied" : reason);
    }
}
