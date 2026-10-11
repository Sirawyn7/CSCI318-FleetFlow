package com.fleetflow.dispatch.infrastructure.persistence;

import com.fleetflow.dispatch.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByOrderId(UUID orderId);

    @org.springframework.data.jpa.repository.Query("select count(a) from Assignment a where a.courierId = :courierId and a.loadAcknowledged = false and a.cancelled = false")
    int countByCourierIdAndLoadAcknowledgedFalse(UUID courierId);
}
