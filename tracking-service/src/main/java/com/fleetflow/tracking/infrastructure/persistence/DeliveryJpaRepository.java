package com.fleetflow.tracking.infrastructure.persistence;

import com.fleetflow.tracking.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Delivery d where d.id = :id")
    java.util.Optional<Delivery> findForCompletion(@org.springframework.data.repository.query.Param("id") UUID id);

    java.util.List<Delivery> findByOrderIdOrderByStartedAtAsc(UUID orderId);
}
