package com.fleetflow.fleet.api;

import java.util.UUID;

public record ChangeAvailabilityResponse(UUID courierId, String status) {
}
