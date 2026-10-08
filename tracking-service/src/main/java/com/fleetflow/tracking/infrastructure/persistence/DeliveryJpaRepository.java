package com.fleetflow.tracking.infrastructure.persistence;

import com.fleetflow.tracking.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID> {
    java.util.List<Delivery> findByCourierId(UUID courierId);
    java.util.List<Delivery> findByCourierIdAndArrivedAtIsNull(UUID courierId);

    @org.springframework.data.jpa.repository.Query("select d.courierId from Delivery d where d.id = :deliveryId")
    java.util.Optional<UUID> findCourierIdByDeliveryId(UUID deliveryId);

    @org.springframework.data.jpa.repository.Query("select distinct d.courierId from Delivery d order by d.courierId")
    java.util.List<UUID> findCourierIds();
}
