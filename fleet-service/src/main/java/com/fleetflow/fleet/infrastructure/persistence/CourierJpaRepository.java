package com.fleetflow.fleet.infrastructure.persistence;

import com.fleetflow.fleet.domain.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierJpaRepository extends JpaRepository<Courier, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Courier c where c.id = :id")
    java.util.Optional<Courier> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
