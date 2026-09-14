package com.fleetflow.order.application;

import com.fleetflow.order.domain.Order;

public interface OrderEventPort {

    void publishOrderPlaced(Order order);

    void publishOrderCancelled(Order order);
}