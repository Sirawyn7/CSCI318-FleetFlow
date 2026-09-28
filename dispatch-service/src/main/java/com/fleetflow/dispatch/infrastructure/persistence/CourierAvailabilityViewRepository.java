package com.fleetflow.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourierAvailabilityViewRepository extends JpaRepository<CourierAvailabilityView, UUID> {

    List<CourierAvailabilityView> findByAvailableTrue();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CourierAvailabilityView c where c.courierId = :id")
    java.util.Optional<CourierAvailabilityView> findForUpdate(UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into courier_availability_view
                (courier_id, vehicle_type, capacity, operating_zone, available, updated_at, active_order_count, load_version)
            values (:id, :vehicleType, :capacity, :zone, :available, :updatedAt, 0, 0)
            """, nativeQuery = true)
    void insertRegistration(UUID id, String vehicleType, int capacity, String zone, boolean available, java.time.Instant updatedAt);
}
