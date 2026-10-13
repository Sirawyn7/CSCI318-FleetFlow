package com.fleetflow.dispatch.infrastructure.persistence;

import com.fleetflow.dispatch.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByOrderId(UUID orderId);
    Optional<Assignment> findByDeliveryId(UUID deliveryId);

    @org.springframework.data.jpa.repository.Query("select count(a) from Assignment a where a.cancelled = false and ("
            + "(a.courierId = :courierId and a.loadAcknowledged = false) or "
            + "(a.previousCourierId = :courierId and a.previousLoadAcknowledged = false))")
    int countByCourierIdAndLoadAcknowledgedFalse(UUID courierId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Assignment a where a.orderId = :orderId")
    Optional<Assignment> findForUpdate(UUID orderId);

    java.util.List<Assignment> findByDeliveryIdIsNotNullAndDeliveryClosedFalse();
    @org.springframework.data.jpa.repository.Query("select a from Assignment a where a.deliveryId is not null "
            + "and a.deliveryClosed = false and a.cancelled = false and a.pendingException is not null "
            + "and a.exceptionAttemptedAt <= :cutoff and a.escalatedAt is null")
    java.util.List<Assignment> findEscalationCandidates(java.time.Instant cutoff);
    java.util.List<Assignment> findByReassignedAtIsNotNullAndReassignmentPublishedFalse();
    java.util.List<Assignment> findByEscalatedAtIsNotNullAndDeliveryClosedFalse();
}
