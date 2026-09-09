package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.order.OrderCancelled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Anti-corruption layer (docs/decisions/0004, rule 4): extracts the order identity
 * Dispatch needs to remove a row from open_orders_view. OrderCancelled carries no
 * other field the view holds, so unlike {@link OrderPlacedTranslator} there is no
 * row to build here — just the boundary crossing.
 */
@Component
public class OrderCancelledTranslator {

    public UUID translate(OrderCancelled event) {
        return event.orderId();
    }
}
