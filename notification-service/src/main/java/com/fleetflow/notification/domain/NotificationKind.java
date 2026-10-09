package com.fleetflow.notification.domain;

/** Value vocabulary for the five FR15 lifecycle messages. */
public enum NotificationKind {
    ASSIGNED("A courier has been assigned to order {orderId}."),
    IN_TRANSIT("Order {orderId} is in transit."),
    NEAR_DESTINATION("Your courier is near the destination for order {orderId}."),
    DELIVERED("Order {orderId} has been delivered."),
    AT_RISK("Order {orderId} may be delayed. Reported reason: {reason}");

    private final String defaultText;
    NotificationKind(String defaultText) { this.defaultText = defaultText; }
    public String defaultText() { return defaultText; }
}
