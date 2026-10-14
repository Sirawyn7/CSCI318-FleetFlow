package com.fleetflow.tracking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryRiskFlagRepository extends JpaRepository<DeliveryRiskFlag, DeliveryRiskFlag.Key> {
    List<DeliveryRiskFlag> findByDeliveryId(UUID deliveryId);
}
