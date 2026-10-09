package com.fleetflow.notification.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface DeliveryOrderLinkRepository extends JpaRepository<DeliveryOrderLink, UUID> { }
