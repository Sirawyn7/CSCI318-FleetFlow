package com.fleetflow.notification.domain;

/**
 * Customer wording for reason codes reported by Tracking (risk) and Dispatch (reassignment).
 * Codes are internal vocabulary: anything unrecognised gets generic wording and is never echoed.
 */
public final class ReasonWording {
    private ReasonWording() { }

    public static String forCustomer(String code) {
        if (code == null || code.isBlank()) return "no reason was given";
        return switch (code) {
            case "STATIONARY" -> "the courier has not moved for a while";
            case "OFF_PACE" -> "the courier is moving more slowly than expected";
            case "SLA_BREACH" -> "the delivery is predicted to arrive after the requested time";
            case "COURIER_OFFLINE" -> "the original courier became unavailable";
            default -> "there is a possible delay";
        };
    }
}
