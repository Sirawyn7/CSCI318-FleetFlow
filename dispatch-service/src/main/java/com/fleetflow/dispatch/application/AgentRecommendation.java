package com.fleetflow.dispatch.application;

import java.util.UUID;

public record AgentRecommendation(UUID orderId, UUID courierId, String justification) { }
