package com.fleetflow.order.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

/** Immutable value recording a transition owned by the Order aggregate. */
@Embeddable
public record OrderStatusChange(@Enumerated(EnumType.STRING) OrderStatus status, Instant changedAt) { }
