package com.fleetflow.fleet.api;

import java.time.Instant;
import java.util.UUID;

public record IngestCourierLocationResponse(UUID courierId, double latitude, double longitude, Instant capturedAt) {
}