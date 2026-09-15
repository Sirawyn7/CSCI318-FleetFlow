package com.fleetflow.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    /** Persist and check database constraints before returning to the publisher. */
    Order save(Order order);

    Optional<Order> findById(UUID id);

    /** Serialize lifecycle changes for this order for the duration of the transaction. */
    Optional<Order> findByIdForUpdate(UUID id);
}