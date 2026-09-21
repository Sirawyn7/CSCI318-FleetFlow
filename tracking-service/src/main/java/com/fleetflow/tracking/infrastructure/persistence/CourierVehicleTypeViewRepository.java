package com.fleetflow.tracking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierVehicleTypeViewRepository extends JpaRepository<CourierVehicleTypeView, UUID> {
}