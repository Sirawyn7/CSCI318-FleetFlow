package com.fleetflow.dispatch.application;

/** Provider-independent port. Agents recommend; only the application can assign. */
public interface DispatchAgent {
    AgentRecommendation recommend(DispatchContext context);
}
