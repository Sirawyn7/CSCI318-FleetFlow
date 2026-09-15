package com.fleetflow.order.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record Address(
        String street,
        String suburb,
        String state,
        String postcode,
        double latitude,
        double longitude
) {
    public Address {
        if (street == null || street.isBlank() || suburb == null || suburb.isBlank()
                || state == null || state.isBlank() || postcode == null || postcode.isBlank()) {
            throw new IllegalArgumentException("All address fields are required");
        }
        if (street.length() > 255 || suburb.length() > 255 || state.length() > 255 || postcode.length() > 255) {
            throw new IllegalArgumentException("Address fields must not exceed 255 characters");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid address coordinates");
        }
    }
}