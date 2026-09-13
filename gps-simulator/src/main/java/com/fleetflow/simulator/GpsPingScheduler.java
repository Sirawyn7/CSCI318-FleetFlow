package com.fleetflow.simulator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Random;
import java.util.UUID;

/**
 * FR10: the simulator is an external REST client posting pings to Fleet, not a Kafka
 * producer (docs/requirements-outline.md, UC5 decision) -- it has no contracts/Kafka
 * dependency at all, just an HTTP call to Fleet's own ingestion endpoint.
 */

@Component
public class GpsPingScheduler {
    private final RestClient restClient;
    private final UUID courierId;
    private final Random random = new Random();

    // Sydney CBD, used as the simulated courier's wandering origin point.
    private double latitude = -33.8688;
    private double longitude = 151.2093;

    public GpsPingScheduler(RestClient.Builder restClientBuilder,
                            @Value("${simulator.fleet-base-url}") String fleetBaseUrl,
                            @Value("${simulator.courier-id}") UUID courierId) {
        this.restClient = restClientBuilder.baseUrl(fleetBaseUrl).build();
        this.courierId = courierId;
    }

    @Scheduled(fixedRateString = "${simulator.ping-interval-ms:5000}")
    public void postPing() {
        // Small random walk each tick so successive pings look like real movement,
        // rather than the same static point repeated forever.
        latitude = (random.nextDouble() - 0.5) * 0.001;
        longitude = (random.nextDouble() - 0.5) * 0.001;

        restClient.post()
                .uri("/api/couriers/{courierId}/location", courierId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LocationPing(latitude, longitude))
                .retrieve()
                .toBodilessEntity();
    }
    private record LocationPing(double latitude, double longitude) {
    }
}
