package com.fleetflow.tracking.infrastructure.persistence;

import com.fleetflow.tracking.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID> {

    /** Newest first for historical data; FR9 transfers the existing delivery without changing its ID. */
    Optional<Delivery> findFirstByOrderIdOrderByStartedAtDesc(UUID orderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Delivery d where d.id = :id")
    java.util.Optional<Delivery> findForCompletion(@org.springframework.data.repository.query.Param("id") UUID id);

    java.util.List<Delivery> findByOrderIdOrderByStartedAtAsc(UUID orderId);

    /** Includes arrived deliveries; never use for active-delivery snapshots. */
    @org.springframework.data.jpa.repository.Query("select d from Delivery d where d.courierId = :courierId")
    java.util.List<Delivery> findAllIncludingArrivedByCourierId(UUID courierId);
    java.util.List<Delivery> findByCourierIdAndArrivedAtIsNullAndCompletedAtIsNullAndCancelledAtIsNull(UUID courierId);

    @org.springframework.data.jpa.repository.Query("select d.courierId from Delivery d where d.id = :deliveryId")
    java.util.Optional<UUID> findCourierIdByDeliveryId(UUID deliveryId);

    @org.springframework.data.jpa.repository.Query("select distinct d.courierId from Delivery d order by d.courierId")
    java.util.List<UUID> findCourierIds();
}
