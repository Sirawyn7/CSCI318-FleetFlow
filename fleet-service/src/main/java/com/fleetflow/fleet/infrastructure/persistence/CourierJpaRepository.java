package com.fleetflow.fleet.infrastructure.persistence;

import com.fleetflow.fleet.domain.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierJpaRepository extends JpaRepository<Courier, UUID> {
}
