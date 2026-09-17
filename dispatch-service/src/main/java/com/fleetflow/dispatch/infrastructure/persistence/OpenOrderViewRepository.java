package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OpenOrderViewRepository extends JpaRepository<OpenOrderView, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from OpenOrderView o where o.orderId = :id")
    java.util.Optional<OpenOrderView> findForUpdate(UUID id);
}
