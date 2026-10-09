package com.fleetflow.notification.application;

import java.time.Instant;
import java.util.*;

/** One request, one order: the model cannot supply arbitrary IDs or URLs to its tools. */
public final class StatusContext {
    private final DeliveryDataPort data;
    private final UUID orderId;
    private OrderFacts order;
    private TrackingFacts tracking;
    private boolean orderRead;
    public StatusContext(DeliveryDataPort data, UUID orderId) {
        this(data, orderId, null);
    }
    public StatusContext(DeliveryDataPort data, UUID orderId, OrderFacts order) {
        this.data = data; this.orderId = orderId; this.order = order;
    }
    public OrderFacts order() {
        orderRead = true;
        if (order == null) {
            try {
                var value = data.order(orderId);
                order = new OrderFacts(true, value.status(), value.requestedDeliveryBy());
            } catch (DeliveryDataPort.Unavailable ex) { order = new OrderFacts(false, null, null); }
        }
        return order;
    }
    public TrackingFacts tracking() {
        if (tracking == null) {
            try {
                var value = data.tracking(orderId);
                tracking = new TrackingFacts(true, value.estimatedArrival(), value.riskFlags(), value.updatedAt());
            } catch (DeliveryDataPort.Unavailable ex) { tracking = new TrackingFacts(false, null, null, null); }
        }
        return tracking;
    }
    public boolean bothRead() { return orderRead && tracking != null; }
    public String fallback() {
        var o = order(); var t = tracking();
        String status = o.available() ? "Order reports status: " + o.status() + "." : "Order status is currently unavailable.";
        String eta = t.available() && t.estimatedArrival() != null
                ? " Latest recorded ETA: " + t.estimatedArrival() + "." : " A current ETA is unavailable.";
        String risk = !t.available() || t.riskFlags() == null ? " Risk information is unavailable."
                : t.riskFlags().isEmpty() ? " Tracking reports no risk flags."
                : " Reported risk flags: " + String.join(", ", t.riskFlags()) + ".";
        String freshness = t.updatedAt() == null ? "" : " Tracking last updated: " + t.updatedAt() + ".";
        return status + eta + risk + freshness + " The conversational explainer is temporarily unavailable; these are recorded facts, not a confirmed cause of delay.";
    }
    public record OrderFacts(boolean available, String status, Instant requestedDeliveryBy) { }
    public record TrackingFacts(boolean available, Instant estimatedArrival, List<String> riskFlags, Instant updatedAt) { }
}
