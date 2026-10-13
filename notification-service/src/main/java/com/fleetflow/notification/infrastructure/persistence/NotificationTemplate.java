package com.fleetflow.notification.infrastructure.persistence;

import com.fleetflow.notification.domain.NotificationKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    @Id @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 64) private NotificationKind code;
    @Column(nullable = false, length = 2000) private String body;
    protected NotificationTemplate() { }
    public NotificationTemplate(NotificationKind code) { this.code = code; this.body = code.defaultText(); }
    public String render(java.util.UUID orderId, String reason) {
        // Previously persisted assignment templates may still contain diagnostic placeholders.
        if (code == NotificationKind.REASSIGNED || code == NotificationKind.ASSIGNMENT_FAILED)
            return code.defaultText().replace("{orderId}", orderId.toString());
        return body.replace("{orderId}", orderId.toString())
                .replace("{reason}", reason == null || reason.isBlank() ? "No reason supplied" : reason);
    }
}
