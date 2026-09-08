package com.fleetflow.fleet.api;

import java.util.UUID;

public record RegisterCourierResponse(UUID courierId, String vehicleType, int capacity, String operatingZone,
                                       String status) {
}
