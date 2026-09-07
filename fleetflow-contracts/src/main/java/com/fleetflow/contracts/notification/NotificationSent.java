package com.fleetflow.contracts.notification;

import com.fleetflow.contracts.EventEnvelope;

import java.util.UUID;

/** Published by Notification on FR15. Terminal — no other service consumes this (§6.1: audit-only). */
public record NotificationSent(
        EventEnvelope envelope,
        UUID notificationId,
        UUID recipientId,
        String channel,
        String templateCode) {
}
