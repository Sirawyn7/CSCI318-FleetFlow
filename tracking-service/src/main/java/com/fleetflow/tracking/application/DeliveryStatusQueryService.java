package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaView;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Read side for UC8/FR16: what Notification's explainer asks Tracking about an order.
 * Null fields mean "unavailable", never "none" -- riskFlags stays null until a risk
 * source (FR13) exists, so the explainer cannot mistake a missing signal for no risk.
 */

@Service
public class DeliveryStatusQueryService {
    private final DeliveryJpaRepository deliveries;
    private final DeliveryEtaViewRepository etaViews;

    public DeliveryStatusQueryService(DeliveryJpaRepository deliveries, DeliveryEtaViewRepository etaViews) {
        this.deliveries = deliveries;
        this.etaViews = etaViews;
    }

    @Transactional(readOnly = true)
    public DeliveryStatus statusForOrder(UUID orderId) {
        Delivery delivery = deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)
                .orElseThrow(() -> new NoSuchElementException("No delivery for order " + orderId));
        DeliveryEtaView eta = etaViews.findById(delivery.getId()).orElse(null);
        return new DeliveryStatus(orderId,
                eta == null ? null : eta.getEstimatedArrival(),
                null,
                eta == null ? null : eta.getObservedAt());
    }

    public record DeliveryStatus (UUID orderId, Instant estimatedArrival, List<String> riskFlags,
                                  Instant updatedAt) {
    }
}
