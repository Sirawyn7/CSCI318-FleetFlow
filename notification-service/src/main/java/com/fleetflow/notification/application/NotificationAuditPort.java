package com.fleetflow.notification.application;
import com.fleetflow.notification.infrastructure.persistence.NotificationRecord;
public interface NotificationAuditPort { void publish(NotificationRecord notification); }
