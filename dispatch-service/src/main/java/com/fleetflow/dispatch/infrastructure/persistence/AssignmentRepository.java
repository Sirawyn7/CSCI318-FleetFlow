package com.fleetflow.dispatch.infrastructure.persistence;

import com.fleetflow.dispatch.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByOrderId(UUID orderId);
}
