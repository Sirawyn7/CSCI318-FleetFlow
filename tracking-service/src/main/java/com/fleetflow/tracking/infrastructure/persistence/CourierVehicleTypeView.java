package com.fleetflow.tracking.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Derived read model (not source-of-truth -- Fleet owns that), projected from
 * CourierRegistered. Exists because OrderAssigned carries only courierId, not the
 * courier's vehicle type, and FR11's average-speed lookup needs it at delivery-start
 * time.
 */
@Entity
@Table(name = "courier_vehicle_type_view")
public class CourierVehicleTypeView {

    @Id
    private UUID courierId;

    private String vehicleType;

    protected CourierVehicleTypeView() {
        // JPA
    }

    public CourierVehicleTypeView(UUID courierId, String vehicleType) {
        this.courierId = courierId;
        this.vehicleType = vehicleType;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public String getVehicleType() {
        return vehicleType;
    }
}