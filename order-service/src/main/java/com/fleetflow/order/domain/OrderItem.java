package com.fleetflow.order.domain;

import jakarta.persistence.Embeddable;

/** Immutable item value; only the Order aggregate owns the collection. */
@Embeddable
public record OrderItem(String description, int quantity) {
    public OrderItem {
        if (description == null || description.isBlank() || description.length() > 255) {
            throw new IllegalArgumentException("Item description must contain 1 to 255 characters");
        }
        if (quantity < 1) throw new IllegalArgumentException("Item quantity must be positive");
    }
}
