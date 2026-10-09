package com.fleetflow.tracking.api;

import com.fleetflow.tracking.application.DeliveryCompletionService;
import com.fleetflow.tracking.domain.Delivery;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final DeliveryCompletionService service;

    public DeliveryController(DeliveryCompletionService service) { this.service = service; }

    public record CompletionRequest(UUID courierId, String routeSummary, String confirmation) {}

    public record DeliveryResponse(UUID id, UUID orderId, UUID courierId, String status,
            Instant startedAt, Instant completedAt, String routeSummary, String confirmation) {
        static DeliveryResponse from(Delivery d) {
            return new DeliveryResponse(d.getId(), d.getOrderId(), d.getCourierId(),
                    d.getCompletedAt() == null ? "IN_TRANSIT" : "DELIVERED", d.getStartedAt(),
                    d.getCompletedAt(), d.getRouteSummary(), d.getConfirmation());
        }
    }

    @PostMapping("/{id}/complete")
    public DeliveryResponse complete(@PathVariable UUID id, @RequestBody CompletionRequest request) {
        return DeliveryResponse.from(service.complete(id, request.courierId(),
                request.routeSummary(), request.confirmation()));
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(@PathVariable UUID id) { return DeliveryResponse.from(service.get(id)); }

    @GetMapping
    public List<DeliveryResponse> history(@RequestParam UUID orderId) {
        return service.history(orderId).stream().map(DeliveryResponse::from).toList();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> missing(NoSuchElementException e) { return Map.of("error", e.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException e) { return Map.of("error", e.getMessage()); }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(IllegalStateException e) { return Map.of("error", e.getMessage()); }
}
