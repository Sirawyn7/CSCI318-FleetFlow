package com.fleetflow.order.api;

import com.fleetflow.order.domain.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, UUID customerId, AddressResponse pickupAddress,
        AddressResponse dropoffAddress, String packageDetails, String serviceLevel,
        Instant requestedDeliveryBy, String status, Instant createdAt, List<StatusResponse> statusHistory, List<ItemResponse> items) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(),
                AddressResponse.from(order.getPickupAddress()), AddressResponse.from(order.getDropoffAddress()),
                order.getPackageDetails(), order.getServiceLevel(), order.getRequestedDeliveryBy(),
                order.getStatus().name(), order.getCreatedAt(), order.getStatusHistory().stream()
                    .map(h -> new StatusResponse(h.status().name(), h.changedAt())).toList(),
                order.getItems().stream().map(item -> new ItemResponse(item.description(), item.quantity())).toList());
    }
    public record ItemResponse(String description, int quantity) { }
    public record StatusResponse(String status, Instant changedAt) { }
    public record AddressResponse(String street, String suburb, String state, String postcode,
                                  double latitude, double longitude) {
        static AddressResponse from(com.fleetflow.order.domain.Address address) {
            return new AddressResponse(address.street(), address.suburb(), address.state(),
                    address.postcode(), address.latitude(), address.longitude());
        }
    }
}
