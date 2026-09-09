package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer (docs/decisions/0004, rule 4): maps Order's published
 * OrderPlaced into Dispatch's own open_orders_view row, and nothing else — no
 * persistence, no idempotency check.
 */
@Component
public class OrderPlacedTranslator {

    public OpenOrderView translate(OrderPlaced event) {
        return new OpenOrderView(
                event.orderId(),
                event.customerId(),
                event.pickupLocation().latitude(),
                event.pickupLocation().longitude(),
                event.dropoffLocation().latitude(),
                event.dropoffLocation().longitude(),
                event.serviceLevel(),
                event.requestedDeliveryBy(),
                event.envelope().occurredAt());
    }
}
