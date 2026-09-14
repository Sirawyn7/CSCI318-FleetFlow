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

    private boolean deactivated;

    private int activeOrderCount;

    @jakarta.persistence.Version
    private long version;

    public void updateProfile(VehicleType vehicleType, int capacity, String operatingZone) {
        if (vehicleType == null || capacity <= 0 || operatingZone == null || operatingZone.isBlank()) {
            throw new IllegalArgumentException("Vehicle type, positive capacity and operating zone are required");
        }
        if (capacity < activeOrderCount) {
            throw new IllegalStateException("Capacity cannot be below current load");
        }
        this.vehicleType = vehicleType;
        this.capacity = capacity;
        this.operatingZone = operatingZone;
    }

    public void deactivate() {
        if (activeOrderCount > 0) {
            throw new IllegalStateException("A courier with active orders cannot be deactivated");
        }
        deactivated = true;
    }

    public void assignOrder() {
        if (deactivated || activeOrderCount >= capacity) {
            throw new IllegalStateException("Courier is deactivated or at capacity");
        }
        activeOrderCount++;
    }

    public void completeOrder() {
        if (activeOrderCount == 0) {
            throw new IllegalStateException("Courier has no active orders");
        }
        activeOrderCount--;
    }

    public boolean isDeactivated() { return deactivated; }

    public int getActiveOrderCount() { return activeOrderCount; }

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
     * publishing CourierAvailabilityChanged) are separate requirements, so a newly
     * registered courier isn't AVAILABLE until {@link #changeAvailability} is called
     * and that event is published.
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

    public void changeAvailability(boolean available) {
        this.status = available ? CourierStatus.AVAILABLE : CourierStatus.OFFLINE;
    }
}
