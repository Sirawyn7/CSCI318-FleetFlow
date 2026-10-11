package com.fleetflow.fleet.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CourierOrderLoadRepository extends JpaRepository<CourierOrderLoad, UUID> {
    @org.springframework.data.jpa.repository.Query("select l.courierId from CourierOrderLoad l where l.orderId = :orderId")
    java.util.Optional<UUID> findCourierIdByOrderId(UUID orderId);
}
