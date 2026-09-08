package com.fleetflow.fleet.application;

/**
 * Application's own input shape — api/ DTOs don't leak into application/, they're
 * translated at the controller boundary.
 */
public record RegisterCourierCommand(String vehicleType, int capacity, String operatingZone) {
}
