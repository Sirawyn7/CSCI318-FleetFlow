package com.fleetflow.fleet.application;

import java.util.UUID;

public record ChangeCourierAvailabilityCommand(UUID courierId, boolean available) {
}
