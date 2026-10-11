package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderCancellationStateRepository extends JpaRepository<OrderCancellationState, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from OrderCancellationState s where s.orderId = :id")
    java.util.Optional<OrderCancellationState> findForUpdate(UUID id);
}
