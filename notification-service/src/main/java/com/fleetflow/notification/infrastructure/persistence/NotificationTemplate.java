package com.fleetflow.notification.infrastructure.persistence;

import com.fleetflow.notification.domain.NotificationKind;
import com.fleetflow.notification.domain.ReasonWording;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    // The kind's name as a plain String: JPA doesn't allow converters on IDs, and an enum ID
    // would get the same CHECK constraint as NotificationRecord.kind (see its comment).
    @Id @Column(length = 64) private String code;
    @Column(nullable = false, length = 2000) private String body;
    protected NotificationTemplate() { }
    public NotificationTemplate(NotificationKind kind) { this.code = kind.name(); this.body = kind.defaultText(); }
    /** The raw reason is an internal code; only its customer wording reaches the message. */
    public String render(java.util.UUID orderId, String reason) {
        return body.replace("{orderId}", orderId.toString())
                .replace("{reason}", ReasonWording.forCustomer(reason));
    }
}
