package com.fleetflow.tracking.api;

import com.fleetflow.tracking.application.DeliveryStatusQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contract documented in notification-service's README (UC8): GET /deliveries/order/{orderId}/status. */
@RestController
public class DeliveryStatusController {

    private final DeliveryStatusQueryService queryService;

    public DeliveryStatusController(DeliveryStatusQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/deliveries/order/{orderId}/status")
    public DeliveryStatusResponse status(@PathVariable UUID orderId) {
        var status = queryService.statusForOrder(orderId);
        return new DeliveryStatusResponse(status.orderId(), status.estimatedArrival(),
                status.riskFlags(), status.updatedAt());
    }

    /** null estimatedArrival = unknown; null riskFlags = risk information unavailable; [] = no risk flags. */
    public record DeliveryStatusResponse(UUID orderId, Instant estimatedArrival, List<String> riskFlags,
                                         Instant updatedAt) {
    }
}
