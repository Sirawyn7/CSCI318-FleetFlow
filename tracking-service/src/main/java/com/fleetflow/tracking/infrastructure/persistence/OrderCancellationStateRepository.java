package com.fleetflow.tracking.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderCancellationStateRepository extends JpaRepository<OrderCancellationState, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderCancellationState s where s.orderId = :id")
    Optional<OrderCancellationState> findForUpdate(@Param("id") UUID id);
}
