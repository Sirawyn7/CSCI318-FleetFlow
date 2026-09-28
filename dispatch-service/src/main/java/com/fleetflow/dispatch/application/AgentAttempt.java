package com.fleetflow.dispatch.application;

public record AgentAttempt(AgentRecommendation recommendation, String fallbackReason) { }
