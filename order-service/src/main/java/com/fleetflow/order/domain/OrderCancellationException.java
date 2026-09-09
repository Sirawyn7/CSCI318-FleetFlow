package com.fleetflow.order.domain;

public class OrderCancellationException extends IllegalStateException {
    public OrderCancellationException() { super("Only placed orders can be cancelled."); }
}
