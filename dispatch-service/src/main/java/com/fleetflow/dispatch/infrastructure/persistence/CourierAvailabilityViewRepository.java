package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourierAvailabilityViewRepository extends JpaRepository<CourierAvailabilityView, UUID> {

    List<CourierAvailabilityView> findByAvailableTrue();
}
