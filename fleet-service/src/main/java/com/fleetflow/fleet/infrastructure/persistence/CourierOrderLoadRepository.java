package com.fleetflow.fleet.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CourierOrderLoadRepository extends JpaRepository<CourierOrderLoad, UUID> {}
