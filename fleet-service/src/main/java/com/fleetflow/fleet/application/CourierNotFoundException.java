package com.fleetflow.fleet.application;

import java.util.UUID;

public class CourierNotFoundException extends RuntimeException {
    public CourierNotFoundException(UUID id) { super("Courier not found: " + id); }
}
