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
