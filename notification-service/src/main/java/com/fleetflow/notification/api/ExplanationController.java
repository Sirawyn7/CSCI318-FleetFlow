package com.fleetflow.notification.api;

import com.fleetflow.notification.application.ExplanationService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/orders/{orderId}/explanations")
public class ExplanationController {
    private final ExplanationService explanations;
    public ExplanationController(ExplanationService explanations) { this.explanations = explanations; }
    @PostMapping
    public ExplanationService.Explanation explain(@PathVariable UUID orderId, @RequestBody Request request) {
        return explanations.explain(orderId, request.conversationId(), request.question());
    }
    public record Request(String question, UUID conversationId) { }
}
