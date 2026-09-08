package com.fleetflow.fleet.api;

public record RegisterCourierRequest(String vehicleType, int capacity, String operatingZone) {
}
