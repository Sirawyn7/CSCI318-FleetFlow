package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourierAvailabilityViewRepository extends JpaRepository<CourierAvailabilityView, UUID> {

    List<CourierAvailabilityView> findByAvailableTrue();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CourierAvailabilityView c where c.available = true order by c.courierId")
    List<CourierAvailabilityView> findAvailableForUpdate();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CourierAvailabilityView c where c.courierId = :id")
    java.util.Optional<CourierAvailabilityView> findForUpdate(UUID id);
}
