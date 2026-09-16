package com.fleetflow.contracts;

/**
 * Kafka topic names for the locked §6 event catalogue. Naming convention:
 * {@code fleetflow.<owning-context>.<event-kebab-case>}. Do not add a topic here
 * without adding the corresponding event name to AGENTS.md / requirements-outline.md §6 first.
 */
public final class Topics {

    public static final String ORDER_PLACED = "fleetflow.order.order-placed";
    public static final String ORDER_CANCELLED = "fleetflow.order.order-cancelled";

    public static final String COURIER_REGISTERED = "fleetflow.fleet.courier-registered";
    public static final String COURIER_AVAILABILITY_CHANGED = "fleetflow.fleet.courier-availability-changed";
    public static final String COURIER_LOCATION_UPDATED = "fleetflow.fleet.courier-location-updated";
    public static final String COURIER_LOAD_CHANGED = "fleetflow.fleet.courier-load-changed";

    public static final String ORDER_ASSIGNED = "fleetflow.dispatch.order-assigned";
    public static final String ORDER_REASSIGNED = "fleetflow.dispatch.order-reassigned";
    public static final String ASSIGNMENT_FAILED = "fleetflow.dispatch.assignment-failed";

    public static final String DELIVERY_STARTED = "fleetflow.tracking.delivery-started";
    public static final String DELIVERY_ETA_UPDATED = "fleetflow.tracking.delivery-eta-updated";
    public static final String DELIVERY_NEAR_DESTINATION = "fleetflow.tracking.delivery-near-destination";
    public static final String DELIVERY_ARRIVED = "fleetflow.tracking.delivery-arrived";
    public static final String DELIVERY_AT_RISK = "fleetflow.tracking.delivery-at-risk";
    public static final String DELIVERY_COMPLETED = "fleetflow.tracking.delivery-completed";

    public static final String NOTIFICATION_SENT = "fleetflow.notification.notification-sent";

    private Topics() {
    }
}
