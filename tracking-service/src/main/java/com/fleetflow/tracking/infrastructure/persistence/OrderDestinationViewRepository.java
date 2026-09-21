package com.fleetflow.tracking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderDestinationViewRepository extends JpaRepository<OrderDestinationView, UUID> {
}