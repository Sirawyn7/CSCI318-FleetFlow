package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentDecisionRepository extends JpaRepository<AssignmentDecision, UUID> {

    Optional<AssignmentDecision> findByOrderId(UUID orderId);
}
