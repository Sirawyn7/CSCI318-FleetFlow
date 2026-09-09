package com.fleetflow.order.infrastructure;

import java.util.UUID;
import com.fleetflow.order.domain.Order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository
        extends JpaRepository<Order, UUID> {
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"statusHistory", "items"})
    java.util.Optional<Order> findById(UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
