package com.fleetflow.notification.infrastructure.rest;

import com.fleetflow.notification.application.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class RestDeliveryDataClient implements DeliveryDataPort {
    private final RestClient orders;
    private final RestClient tracking;
    private final String trackingPath;
    @org.springframework.beans.factory.annotation.Autowired
    public RestDeliveryDataClient(@Value("${notification.services.order-url:http://localhost:8081}") String orderUrl,
            @Value("${notification.services.tracking-url:http://localhost:8084}") String trackingUrl,
            @Value("${notification.services.tracking-path:/deliveries/order/{orderId}/status}") String trackingPath) {
        this(client(orderUrl), client(trackingUrl), trackingPath);
    }
    RestDeliveryDataClient(RestClient orders, RestClient tracking, String trackingPath) {
        this.orders = orders; this.tracking = tracking; this.trackingPath = trackingPath;
    }
    private static RestClient client(String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public DeliveryData.Order order(UUID id) {
        try {
            var result = orders.get().uri("/orders/{id}", id).retrieve().body(DeliveryData.Order.class);
            if (result == null || !id.equals(result.id()) || result.customerId() == null || result.status() == null)
                throw new Unavailable("Order");
            return result;
        } catch (HttpClientErrorException.NotFound ex) { throw new OrderNotFound(); }
        catch (RestClientException ex) { throw new Unavailable("Order"); }
    }
    public DeliveryData.Tracking tracking(UUID id) {
        try {
            var result = tracking.get().uri(trackingPath, id).retrieve().body(DeliveryData.Tracking.class);
            if (result == null || !id.equals(result.orderId())) throw new Unavailable("Tracking");
            return result;
        } catch (RestClientException ex) { throw new Unavailable("Tracking"); }
    }
}
