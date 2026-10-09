package com.fleetflow.notification.infrastructure.persistence;
import com.fleetflow.notification.domain.NotificationKind;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, NotificationKind> { }
