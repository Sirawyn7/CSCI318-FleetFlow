package com.fleetflow.tracking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierVehicleTypeViewRepository extends JpaRepository<CourierVehicleTypeView, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CourierVehicleTypeView c where c.courierId = :id")
    java.util.Optional<CourierVehicleTypeView> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
