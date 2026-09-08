package com.fleetflow.order.domain;

public record Address(
        String street,
        String suburb,
        String state,
        String postcode,
        double latitude,
        double longitude
) {
}