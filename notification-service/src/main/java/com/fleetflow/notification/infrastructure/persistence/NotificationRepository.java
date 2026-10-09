package com.fleetflow.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {
    @Query("""
            select n from NotificationRecord n
            where n.sentAt is null
              and (:createdAt is null or n.createdAt > :createdAt
                   or (n.createdAt = :createdAt and n.id > :id))
            order by n.createdAt asc, n.id asc
            """)
    List<NotificationRecord> findNextPending(@Param("createdAt") Instant createdAt,
                                             @Param("id") UUID id, Pageable page);
    @Query("""
            select n from NotificationRecord n
            where n.sentAt is not null and n.auditPublished = false
              and (:sentAt is null or n.sentAt > :sentAt
                   or (n.sentAt = :sentAt and n.id > :id))
            order by n.sentAt asc, n.id asc
            """)
    List<NotificationRecord> findNextAudit(@Param("sentAt") Instant sentAt,
                                            @Param("id") UUID id, Pageable page);
    List<NotificationRecord> findByRecipientIdAndSentAtIsNotNullOrderByOccurredAtDesc(UUID recipientId, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationRecord n where n.id = :id")
    Optional<NotificationRecord> lockById(UUID id);
}
