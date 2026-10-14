package com.fleetflow.fleet.application;

import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.VehicleType;
import com.fleetflow.fleet.infrastructure.messaging.CourierAvailabilityChangedEventPublisher;
import com.fleetflow.fleet.infrastructure.persistence.CourierJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CourierManagementService {
    private final CourierJpaRepository couriers;
    private final CourierAvailabilityChangedEventPublisher availability;
    public CourierManagementService(CourierJpaRepository couriers, CourierAvailabilityChangedEventPublisher availability) {
        this.couriers = couriers;
        this.availability = availability;
    }

    @Transactional(readOnly = true)
    public List<Courier> list() { return couriers.findAll(); }

    @Transactional(readOnly = true)
    public Courier get(UUID id) {
        return couriers.findById(id).orElseThrow(() -> new CourierNotFoundException(id));
    }

    public Courier update(UUID id, RegisterCourierCommand command) {
        Courier courier = locked(id);
        if (command.vehicleType() == null) throw new IllegalArgumentException("vehicleType is required");
        courier.updateProfile(VehicleType.valueOf(command.vehicleType().toUpperCase(Locale.ROOT)),
                command.capacity(), command.operatingZone());
        return courier;
    }

    public Courier deactivate(UUID id) {
        Courier courier = locked(id);
        if (courier.deactivate()) {
            // Publishing inside the transaction: a failed send rolls the deactivation back.
            couriers.saveAndFlush(courier);
            availability.publish(courier);
        }
        return courier;
    }

    private Courier locked(UUID id) {
        return couriers.findForUpdate(id).orElseThrow(() -> new CourierNotFoundException(id));
    }
}
