package com.fleetflow.fleet.application;

import java.util.UUID;

public record IngestCourierLocationCommand(UUID courierId, double latitude, double longitude) {
}
