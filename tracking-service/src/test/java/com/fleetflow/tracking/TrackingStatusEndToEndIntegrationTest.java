package com.fleetflow.tracking;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaView;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryEtaViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationViewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Status endpoint end to end against a real broker, through the real binder: the same
 * bootstrap path as Fr11EndToEndIntegrationTest, then a location ping makes the FR11
 * topology publish DeliveryEtaUpdated, Tracking's own projection consumer
 * ({@code deliveryEtaProjectionConsumer}) fills delivery_eta_view from that real event,
 * and GET /deliveries/order/{orderId}/status reports it. If the consumer bean name and
 * the function definition in application.yml ever disagree, the view never fills and
 * this test times out -- the service-level tests cannot catch that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TrackingStatusEndToEndIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.functions.etaRecalculation.application-id",
                () -> "tracking-it-status-eta-" + UUID.randomUUID());
        registry.add("spring.cloud.stream.kafka.streams.binder.functions.nearDestinationDetection.application-id",
                () -> "tracking-it-status-near-" + UUID.randomUUID());
        registry.add("spring.cloud.stream.kafka.streams.binder.functions.arrivedDetection.application-id",
                () -> "tracking-it-status-arrived-" + UUID.randomUUID());
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:tracking-status-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired StreamBridge streamBridge;
    @Autowired MockMvc mockMvc;
    @Autowired OrderDestinationViewRepository destinationRepository;
    @Autowired CourierVehicleTypeViewRepository vehicleTypeRepository;
    @Autowired DeliveryJpaRepository deliveryRepository;
    @Autowired DeliveryEtaViewRepository etaViewRepository;

    @Test
    void realEtaEventFillsTheViewAndTheEndpointReportsIt() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        streamBridge.send(Topics.ORDER_PLACED, new OrderPlaced(
                newEnvelope("OrderPlaced"), orderId, UUID.randomUUID(),
                new GeoPoint(-33.87, 151.20), new GeoPoint(-33.8688, 151.2093),
                "STANDARD", Instant.now().plusSeconds(3600)));
        streamBridge.send(Topics.COURIER_REGISTERED, new CourierRegistered(
                newEnvelope("CourierRegistered"), courierId, "VAN", 20, "ZONE_A"));

        await("order_destination_view row", () -> destinationRepository.findById(orderId).isPresent());
        await("courier_vehicle_type_view row", () -> vehicleTypeRepository.findById(courierId).isPresent());

        streamBridge.send(Topics.ORDER_ASSIGNED, new OrderAssigned(
                newEnvelope("OrderAssigned"), orderId, courierId, UUID.randomUUID()));

        await("Delivery bootstrapped", () -> deliveryRepository.findFirstByOrderIdOrderByStartedAtDesc(orderId).isPresent());
        UUID deliveryId = deliveryRepository.findFirstByOrderIdOrderByStartedAtDesc(orderId).orElseThrow().getId();

        // Before any ping there is no ETA: delivery exists, so 200 with unknown ETA (not 404).
        mockMvc.perform(get("/deliveries/order/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedArrival").doesNotExist())
                .andExpect(jsonPath("$.riskFlags").doesNotExist());

        // Settle period for the Streams topology's KTable to materialize the snapshot.
        Thread.sleep(5000);

        Message<CourierLocationUpdated> ping = MessageBuilder.withPayload(
                        new CourierLocationUpdated(newEnvelope("CourierLocationUpdated"), courierId,
                                new GeoPoint(-33.90, 151.15), Instant.now()))
                .setHeader(KafkaHeaders.KEY, courierId.toString())
                .build();
        streamBridge.send(Topics.COURIER_LOCATION_UPDATED, ping);

        // The view can only fill from a real DeliveryEtaUpdated consumed by the real binder.
        await("delivery_eta_view row from a real DeliveryEtaUpdated",
                () -> etaViewRepository.findById(deliveryId).isPresent(), 40000);
        DeliveryEtaView view = etaViewRepository.findById(deliveryId).orElseThrow();

        mockMvc.perform(get("/deliveries/order/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.estimatedArrival").value(view.getEstimatedArrival().toString()))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.riskFlags").doesNotExist());
        assertEquals(deliveryId, view.getDeliveryId());
    }

    @Test
    void unknownOrderIs404WithMessage() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(get("/deliveries/order/{id}/status", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No delivery for order " + unknown));
    }

    private EventEnvelope newEnvelope(String eventType) {
        return new EventEnvelope(UUID.randomUUID(), eventType, Instant.now(), UUID.randomUUID().toString());
    }

    private void await(String what, java.util.function.BooleanSupplier condition) throws InterruptedException {
        await(what, condition, 15000);
    }

    private void await(String what, java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for " + what);
    }
}