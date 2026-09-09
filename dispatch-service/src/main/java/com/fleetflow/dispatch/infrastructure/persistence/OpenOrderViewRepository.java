package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OpenOrderViewRepository extends JpaRepository<OpenOrderView, UUID> {
}
