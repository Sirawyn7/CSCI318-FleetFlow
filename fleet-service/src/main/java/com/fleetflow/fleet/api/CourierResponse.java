package com.fleetflow.fleet.api;

import com.fleetflow.fleet.domain.Courier;
import java.util.UUID;

public record CourierResponse(UUID id, String vehicleType, int capacity, String operatingZone,
                              String status, boolean deactivated, int activeOrderCount) {
    public static CourierResponse from(Courier courier) {
        return new CourierResponse(courier.getId(), courier.getVehicleType().name(), courier.getCapacity(),
                courier.getOperatingZone(), courier.getStatus().name(), courier.isDeactivated(),
                courier.getActiveOrderCount());
    }
}
