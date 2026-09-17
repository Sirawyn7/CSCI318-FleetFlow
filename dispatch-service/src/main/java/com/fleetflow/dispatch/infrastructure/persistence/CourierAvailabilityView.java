package com.fleetflow.dispatch.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Derived read model (docs/requirements-outline.md §7, §8): Dispatch's own compacted
 * view of Fleet's courier state, kept current by consuming Fleet's events. Not a domain
 * aggregate and carries no invariants of its own — the same reasoning §8 applies
 * explicitly to processed_events ("infrastructure concern, not a domain entity... does
 * not appear in the DDD pattern classification") applies here, so this lives in
 * infrastructure/persistence/ rather than domain/. Assignment is the domain aggregate.
 */
@Entity
@Table(name = "courier_availability_view")
public class CourierAvailabilityView {

    @Id
    private UUID courierId;

    private String vehicleType;

    private int capacity;

    private int activeOrderCount;

    private long loadVersion;

    public int getActiveOrderCount() { return activeOrderCount; }
    public long getLoadVersion() { return loadVersion; }

    public void updateLoad(int activeOrderCount, int capacity, long version) {
        if (version < loadVersion) return;
        if (activeOrderCount < 0 || capacity <= 0 || activeOrderCount > capacity || version < 0) {
            throw new IllegalArgumentException("Invalid courier load");
        }
        this.activeOrderCount = activeOrderCount;
        this.capacity = capacity;
        this.loadVersion = version;
    }

    private String operatingZone;

    private boolean available;

    private Instant updatedAt;

    // Registration time must not order events on the separate availability stream.
    private Instant availabilityUpdatedAt;

    public Instant getAvailabilityUpdatedAt() { return availabilityUpdatedAt; }

    // Nullable, unlike OpenOrderView's pickup/dropoff coordinates: an order's
    // coordinates always exist by construction, but a courier's location is
    // genuinely absent until its first CourierLocationUpdated ping arrives.
    private Double latitude;

    private Double longitude;

    private Instant locationUpdatedAt;

    protected CourierAvailabilityView() {
        // JPA
    }

    public CourierAvailabilityView(UUID courierId, String vehicleType, int capacity, String operatingZone,
                                    boolean available, Instant updatedAt) {
        this.courierId = courierId;
        this.vehicleType = vehicleType;
        this.capacity = capacity;
        this.operatingZone = operatingZone;
        this.available = available;
        this.updatedAt = updatedAt;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getOperatingZone() {
        return operatingZone;
    }

    public boolean isAvailable() {
        return available;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markAvailability(boolean available, Instant updatedAt) {
        this.available = available;
        this.updatedAt = updatedAt;
        this.availabilityUpdatedAt = updatedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void updateLocation(double latitude, double longitude, Instant updatedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationUpdatedAt = updatedAt;
    }
}
