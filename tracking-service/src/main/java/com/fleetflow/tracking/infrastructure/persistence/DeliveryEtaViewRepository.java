package com.fleetflow.tracking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryEtaViewRepository extends JpaRepository<DeliveryEtaView, UUID> {
}