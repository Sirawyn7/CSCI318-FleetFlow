package com.fleetflow.fleet.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Infrastructure ledger for cross-topic ordering and semantic duplicates.
 * The capacity invariant remains on Courier; this is not a Dispatch assignment.
 * Completed rows are retained so a late assignment cannot restore completed load.
 */
@Entity
@Table(name = "courier_order_load")
public class CourierOrderLoad {
    @Id
    private UUID orderId;
    private UUID courierId;
    private boolean completed;
    private UUID previousCourierId;
