package com.fleetflow.tracking.infrastructure.persistence;

import com.fleetflow.tracking.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID> {

    /** Newest first, so a future reassignment (FR9) that starts a second delivery wins. */
    Optional<Delivery> findFirstByOrderIdOrderByStartedAtDesc(UUID orderId);
}