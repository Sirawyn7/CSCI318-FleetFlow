package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierAvailabilityViewRepository extends JpaRepository<CourierAvailabilityView, UUID> {
}
