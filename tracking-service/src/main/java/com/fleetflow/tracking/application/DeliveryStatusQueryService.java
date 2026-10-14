package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaView;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlag;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryRiskFlagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Read side for UC8/FR16: what Notification's explainer asks Tracking about an order.
 * A null ETA means none has been calculated yet. riskFlags lists the reasons currently
 * raised; [] means none, and is reported for a delivery that has ended.
 */
@Service
public class DeliveryStatusQueryService {
    private final DeliveryJpaRepository deliveries;
    private final DeliveryEtaViewRepository etaViews;
    private final DeliveryRiskFlagRepository riskFlags;
    private final Duration riskFreshness;
    private final Clock clock;

    /**
     * No event clears a risk. Detection re-raises a reason that still holds once its alert
     * cooldown has passed, at the next ping, which is at most one ping gap later. A reason
     * not raised again within that time is treated as no longer current.
     */
    @Autowired
    public DeliveryStatusQueryService(DeliveryJpaRepository deliveries, DeliveryEtaViewRepository etaViews,
            DeliveryRiskFlagRepository riskFlags,
            @Value("${tracking.risk.alert-cooldown:PT10M}") Duration alertCooldown,
            @Value("${tracking.risk.maximum-ping-gap:PT2M}") Duration maximumPingGap) {
        this(deliveries, etaViews, riskFlags, alertCooldown.plus(maximumPingGap), Clock.systemUTC());
    }

    DeliveryStatusQueryService(DeliveryJpaRepository deliveries, DeliveryEtaViewRepository etaViews,
            DeliveryRiskFlagRepository riskFlags, Duration riskFreshness, Clock clock) {
        this.deliveries = deliveries;
        this.etaViews = etaViews;
        this.riskFlags = riskFlags;
        this.riskFreshness = riskFreshness;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DeliveryStatus statusForOrder(UUID orderId) {
        Delivery delivery = deliveries.findFirstByOrderIdOrderByStartedAtDesc(orderId)
                .orElseThrow(() -> new NoSuchElementException("No delivery for order " + orderId));
        DeliveryEtaView eta = etaViews.findById(delivery.getId()).orElse(null);
        return new DeliveryStatus(orderId,
                eta == null ? null : eta.getEstimatedArrival(),
                currentRiskFlags(delivery),
                eta == null ? null : eta.getObservedAt());
    }

    private List<String> currentRiskFlags(Delivery delivery) {
        if (delivery.getArrivedAt() != null || delivery.getCompletedAt() != null || delivery.getCancelledAt() != null) {
            return List.of();
        }
        Instant freshSince = clock.instant().minus(riskFreshness);
        // Alerts raised for a previous courier describe that courier's movement, not this one's.
        Instant courierSince = delivery.getMonitoringStartedAt();
        return riskFlags.findByDeliveryId(delivery.getId()).stream()
                .filter(flag -> !flag.getRaisedAt().isBefore(courierSince) && flag.getRaisedAt().isAfter(freshSince))
                .map(DeliveryRiskFlag::getReason)
                .sorted()
                .toList();
    }

    public record DeliveryStatus (UUID orderId, Instant estimatedArrival, List<String> riskFlags,
                                  Instant updatedAt) {
    }
}
