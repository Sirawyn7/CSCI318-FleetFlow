package com.fleetflow.notification.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
/** Keyed by NotificationKind.name(); see NotificationTemplate. */
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> { }
