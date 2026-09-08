package com.fleetflow.fleet.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Aggregate root (FR4). Identity persists through change, so this is an Entity, not a
 * Value Object, per docs/AGENTS.md §9.3. Registration invariants are enforced in the
 * {@link #register} factory so the aggregate can never be constructed in an invalid
 * state, regardless of caller (api/, tests, or otherwise).
 */
@Entity
@Table(name = "couriers")
public class Courier {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    private int capacity;

    private String operatingZone;

    @Enumerated(EnumType.STRING)
    private CourierStatus status;

    protected Courier() {
        // JPA
    }

    private Courier(UUID id, VehicleType vehicleType, int capacity, String operatingZone, CourierStatus status) {
        this.id = id;
        this.vehicleType = vehicleType;
        this.capacity = capacity;
        this.operatingZone = operatingZone;
        this.status = status;
    }

    /**
     * Status defaults to OFFLINE: FR4 (registration) and FR5 (on/off-shift availability,
     * publishing CourierAvailabilityChanged) are separate requirements, and that event
     * isn't consumed yet in this slice — a courier isn't AVAILABLE until it fires.
     */
    public static Courier register(VehicleType vehicleType, int capacity, String operatingZone) {
        if (vehicleType == null) {
            throw new IllegalArgumentException("vehicleType is required");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (operatingZone == null || operatingZone.isBlank()) {
            throw new IllegalArgumentException("operatingZone is required");
        }
        return new Courier(UUID.randomUUID(), vehicleType, capacity, operatingZone, CourierStatus.OFFLINE);
    }

    public UUID getId() {
        return id;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getOperatingZone() {
        return operatingZone;
    }

    public CourierStatus getStatus() {
        return status;
    }
}
