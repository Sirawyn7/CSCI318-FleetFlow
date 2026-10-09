package com.fleetflow.notification.application;

import java.util.UUID;

public interface DeliveryDataPort {
    DeliveryData.Order order(UUID orderId);
    DeliveryData.Tracking tracking(UUID orderId);

    class OrderNotFound extends RuntimeException { }
    class Unavailable extends RuntimeException {
        public Unavailable(String service) { super(service + " data is unavailable"); }
    }
}
