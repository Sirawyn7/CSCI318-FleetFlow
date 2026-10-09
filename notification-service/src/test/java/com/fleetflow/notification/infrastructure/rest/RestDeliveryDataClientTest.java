package com.fleetflow.notification.infrastructure.rest;

import com.fleetflow.notification.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestDeliveryDataClientTest {
    @Test void readsExistingOrderContractAndProposedTrackingContract() {
        UUID order = UUID.randomUUID(), customer = UUID.randomUUID();
        var builder = RestClient.builder().baseUrl("http://services");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RestDeliveryDataClient(builder.build(), builder.build(), "/deliveries/order/{orderId}/status");
        server.expect(requestTo("http://services/orders/" + order)).andRespond(withSuccess(
                "{\"id\":\"" + order + "\",\"customerId\":\"" + customer + "\",\"status\":\"PLACED\",\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://services/deliveries/order/" + order + "/status")).andRespond(withSuccess(
                "{\"orderId\":\"" + order + "\",\"estimatedArrival\":null,\"riskFlags\":[\"Stationary\"],\"updatedAt\":null}", MediaType.APPLICATION_JSON));
        assertEquals(customer, client.order(order).customerId());
        assertEquals("Stationary", client.tracking(order).riskFlags().getFirst());
        server.verify();
    }
    @Test void missingOrderIsNotFoundButMissingTrackingIsUnavailable() {
        var builder = RestClient.builder().baseUrl("http://services");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RestDeliveryDataClient(builder.build(), builder.build(), "/tracking/{id}");
        server.expect(anything()).andRespond(withResourceNotFound());
        server.expect(anything()).andRespond(withResourceNotFound());
        assertThrows(DeliveryDataPort.OrderNotFound.class, () -> client.order(UUID.randomUUID()));
        assertThrows(DeliveryDataPort.Unavailable.class, () -> client.tracking(UUID.randomUUID()));
        server.verify();
    }
}
