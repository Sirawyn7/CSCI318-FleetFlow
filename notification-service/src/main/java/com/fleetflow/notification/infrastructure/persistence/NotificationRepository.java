package com.fleetflow.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {
    List<NotificationRecord> findBySentAtIsNullOrderByCreatedAtAsc(Pageable page);
    List<NotificationRecord> findBySentAtIsNotNullAndAuditPublishedFalseOrderBySentAtAsc(Pageable page);
    List<NotificationRecord> findByRecipientIdAndSentAtIsNotNullOrderByOccurredAtDesc(UUID recipientId, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationRecord n where n.id = :id")
    Optional<NotificationRecord> lockById(UUID id);
}
