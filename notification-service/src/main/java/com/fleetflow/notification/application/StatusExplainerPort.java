package com.fleetflow.notification.application;
public interface StatusExplainerPort {
    String explain(String question, String history, StatusContext context);
}
