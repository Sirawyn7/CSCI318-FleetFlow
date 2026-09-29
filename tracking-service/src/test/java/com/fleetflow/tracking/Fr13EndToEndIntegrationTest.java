package com.fleetflow.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.contracts.tracking.DeliveryAtRisk;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import com.fleetflow.contracts.tracking.DeliveryNearDestination;
import com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import com.fleetflow.tracking.infrastructure.persistence.OrderDestinationViewRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR13 through the real broker, JPA bootstrap, courier fan-out, window store and output binding. */

@org.springframework.test.annotation.DirtiesContext
@SpringBootTest
@Testcontainers
public class Fr13EndToEndIntegrationTest {

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.functions.etaRecalculation.application-id",
                () -> "uc6-eta-" + RUN_ID);
        registry.add("spring.cloud.stream.kafka.streams.binder.functions.nearDestinationDetection.application-id",
                () -> "uc6-near-" + RUN_ID);
        registry.add("spring.cloud.stream.kafka.streams.binder.functions.arrivedDetection.application-id",
                () -> "uc6-arrived-" + RUN_ID);
        registry.add("spring.cloud.stream.kafka.streams.binder.configuration.state.dir", () -> System.getProperty("java.io.tmpdir") + "/uc6-" + RUN_ID);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:tracking-fr13-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private OrderDestinationViewRepository destinationRepository;

    @Autowired
    private CourierVehicleTypeViewRepository vehicleTypeRepository;

    @Autowired
    private DeliveryJpaRepository deliveryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private KafkaConsumer<String, byte[]> riskConsumer;


    @BeforeEach
    void setUpConsumers() {
        riskConsumer = subscribedConsumer(Topics.DELIVERY_AT_RISK);
    }

    private KafkaConsumer<String, byte[]> subscribedConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "fr13-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    @AfterEach
    void tearDownConsumers() {
        riskConsumer.close();
    }

    @Test
    void detectsAllRisksForTwoDeliveriesAndSuppressesDuplicateAndStalePings() throws Exception {
        UUID courier = UUID.randomUUID();
        streamBridge.send(Topics.COURIER_REGISTERED, new CourierRegistered(
                newEnvelope("CourierRegistered"), courier, "VAN", 20, "ZONE_A"));
        awaitVehicleTypeProjected(courier);
        Set<UUID> orders = new HashSet<>();
        Set<UUID> deliveries = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            UUID order = UUID.randomUUID(); orders.add(order);
            streamBridge.send(Topics.ORDER_PLACED, new OrderPlaced(newEnvelope("OrderPlaced"), order,
                    UUID.randomUUID(), new GeoPoint(-33.8,151.2), new GeoPoint(-33.9,151.2),
                    "STANDARD", Instant.now().minusSeconds(60)));
            awaitDestinationProjected(order);
            streamBridge.send(Topics.ORDER_ASSIGNED, new OrderAssigned(newEnvelope("OrderAssigned"), order,
                    courier, UUID.randomUUID()));
            deliveries.add(awaitDeliveryBootstrapped(order));
        }
        // Retry actual pings until both joined snapshots are observable; no fixed startup sleep.
        Set<UUID> slaSeen = new HashSet<>();
        Instant time = Instant.now();
        long readyDeadline = System.currentTimeMillis() + 45000;
        while (slaSeen.size() < 2 && System.currentTimeMillis() < readyDeadline) {
            time = time.plusSeconds(1);
            sendPing(courier, time, -33.8);
            for (var record : riskConsumer.poll(Duration.ofMillis(500))) {
                DeliveryAtRisk event = objectMapper.readValue(record.value(), DeliveryAtRisk.class);
                assertEquals("SLA_BREACH", event.riskReason());
                assertTrue(orders.contains(event.orderId()));
                assertEquals(courier, event.courierId());
                assertEquals(event.deliveryId().toString(), record.key());
                slaSeen.add(event.deliveryId());
            }
        }
        assertEquals(deliveries, slaSeen);
        Instant baseline = time;
        for (int i = 1; i <= 5; i++) sendPing(courier, baseline.plusSeconds(i * 60), -33.8);
        assertEquals(deliveries, awaitRisk("STATIONARY"));
        // Beyond the rolling window: moving away from the destination produces OFF_PACE.
        baseline = baseline.plusSeconds(1200);
        for (int i = 0; i <= 5; i++) sendPing(courier, baseline.plusSeconds(i * 60), -33.8 + i * 0.001);
        assertEquals(deliveries, awaitRisk("OFF_PACE"));
        sendPing(courier, baseline.plusSeconds(300), -33.795); // duplicate timestamp
        sendPing(courier, baseline, -33.8); // stale
        sendPing(courier, baseline.plusSeconds(301), -33.795); // cooldown
        sendPing(UUID.randomUUID(), baseline.plusSeconds(302), -33.8); // no assignment
        long quietDeadline = System.currentTimeMillis() + 2500;
        while (System.currentTimeMillis() < quietDeadline)
            assertTrue(riskConsumer.poll(Duration.ofMillis(250)).isEmpty());
    }

    private void sendPing(UUID courier, Instant capturedAt, double latitude) {
        streamBridge.send(Topics.COURIER_LOCATION_UPDATED, MessageBuilder.withPayload(
                new CourierLocationUpdated(newEnvelope("CourierLocationUpdated"), courier,
                        new GeoPoint(latitude,151.2), capturedAt))
                .setHeader(KafkaHeaders.KEY,courier.toString()).build());
    }

    private Set<UUID> awaitRisk(String reason) throws Exception {
        Set<UUID> ids = new HashSet<>();
        long deadline = System.currentTimeMillis() + 30000;
        while (ids.size() < 2 && System.currentTimeMillis() < deadline) {
            for (var record : riskConsumer.poll(Duration.ofMillis(500))) {
                var event = objectMapper.readValue(record.value(),DeliveryAtRisk.class);
                if (event.riskReason().equals(reason)) ids.add(event.deliveryId());
            }
        }
        return ids;
    }

    private EventEnvelope newEnvelope(String eventType) {
        return new EventEnvelope(UUID.randomUUID(), eventType, Instant.now(), UUID.randomUUID().toString());
    }

    private void awaitDestinationProjected(UUID orderId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (destinationRepository.findById(orderId).isPresent()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for order_destination_view row");
    }

    private void awaitVehicleTypeProjected(UUID courierId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (vehicleTypeRepository.findById(courierId).isPresent()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for courier_vehicle_type_view row");
    }

    private UUID awaitDeliveryBootstrapped(UUID orderId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            var match = deliveryRepository.findAll().stream()
                    .filter(delivery -> delivery.getOrderId().equals(orderId))
                    .findFirst();
            if (match.isPresent()) {
                return match.get().getId();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for Delivery to be bootstrapped");
    }

    private <T> T awaitEvent(KafkaConsumer<String, byte[]> consumer, Class<T> type, String topic) throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (var record : records) {
                return objectMapper.readValue(record.value(), type);
            }
        }
        throw new AssertionError("Timed out waiting for an event on " + topic);
    }
}

