package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.fleet.CourierAvailabilityChanged;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentDecision;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentDecisionRepository;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentRepository;
import com.fleetflow.dispatch.infrastructure.persistence.DecisionOutcome;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentPath;
import com.fleetflow.dispatch.application.DispatchAgent;
import com.fleetflow.dispatch.application.AgentRecommendation;
import com.fleetflow.contracts.fleet.CourierLoadChanged;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.time.Duration;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives FR7's whole path -- open_orders_view projection, rule-based selection,
 * assignments/assignment_decisions writes, open_orders_view removal, OrderAssigned/
 * AssignmentFailed publication -- against a real Kafka broker, same shape as
 * OpenOrdersConsumerIntegrationTest.
 */
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AssignmentIntegrationTest {

    @MockitoBean DispatchAgent agent;
    @Autowired CourierAvailabilityViewRepository couriers;
    @Autowired ProcessedEventRepository processed;

    @BeforeEach
    void isolateTests() {
        assignmentRepository.deleteAll(); assignmentDecisionRepository.deleteAll();
        openOrderViewRepository.deleteAll(); couriers.deleteAll(); processed.deleteAll();
        when(agent.recommend(any())).thenThrow(new IllegalStateException("Test provider unavailable"));
    }

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dispatch-assignment-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private OpenOrderViewRepository openOrderViewRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private AssignmentDecisionRepository assignmentDecisionRepository;

    @Test
    void orderPlacedNearAnAvailableCourierIsAssignedAndRemovedFromTheBacklog() throws InterruptedException {
        UUID courierId = UUID.randomUUID();
        registerAvailableCourierAt(courierId, -34.41, 150.81);

        UUID orderId = UUID.randomUUID();
        publishOrderPlaced(orderId, -34.4, 150.8);

        awaitDecisionPresent(orderId);

        AssignmentDecision decision = assignmentDecisionRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(DecisionOutcome.ASSIGNED, decision.getOutcome());
        assertEquals(courierId, decision.getCourierId());

        assertTrue(assignmentRepository.findByOrderId(orderId).isPresent());
        assertEquals(courierId, assignmentRepository.findByOrderId(orderId).orElseThrow().getCourierId());
        assertFalse(openOrderViewRepository.existsById(orderId));
        assertEquals(AssignmentPath.RULE_BASED, decision.getPathUsed());
        assertPublished(Topics.ORDER_ASSIGNED, orderId);
    }

    @Test
    void orderPlacedWithNoAvailableCourierFailsAndStaysInTheBacklog() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        publishOrderPlaced(orderId, -34.4, 150.8);

        awaitDecisionPresent(orderId);

        AssignmentDecision decision = assignmentDecisionRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(DecisionOutcome.FAILED, decision.getOutcome());
        assertNull(decision.getCourierId());

        assertTrue(assignmentRepository.findByOrderId(orderId).isEmpty());
        assertTrue(openOrderViewRepository.existsById(orderId));
        assertPublished(Topics.ASSIGNMENT_FAILED, orderId);
    }

    @Test
    void agentAssignmentAndLoadAcknowledgementTravelThroughKafka() throws InterruptedException {
        UUID courierId = UUID.randomUUID();
        registerAvailableCourierAt(courierId, -34.41, 150.81);
        doAnswer(invocation -> {
            var context = invocation.getArgument(0, com.fleetflow.dispatch.application.DispatchContext.class);
            return new AgentRecommendation(context.targetOrderId(), courierId, "Nearby with five free slots");
        }).when(agent).recommend(any());
        UUID orderId = UUID.randomUUID();
        publishOrderPlaced(orderId, -34.4, 150.8);
        awaitDecisionPresent(orderId);
        var decision = assignmentDecisionRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(AssignmentPath.AGENT, decision.getPathUsed());
        assertEquals("Nearby with five free slots", decision.getReason());
        assertPublished(Topics.ORDER_ASSIGNED, orderId);

        var envelope = new EventEnvelope(UUID.randomUUID(), "CourierLoadChanged", Instant.now(), orderId.toString());
        streamBridge.send(Topics.COURIER_LOAD_CHANGED, new CourierLoadChanged(envelope, courierId, 1, 5, 1, orderId));
        await(() -> processed.existsById(envelope.eventId()));
        assertEquals(1, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
        assertEquals(0, assignmentRepository.countByCourierIdAndLoadAcknowledgedFalse(courierId));
    }

    @Test
    void duplicateOrderPlacedDeliveryProducesExactlyOneDecision() throws InterruptedException {
        UUID courierId = UUID.randomUUID();
        registerAvailableCourierAt(courierId, -34.41, 150.81);

        UUID orderId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), "OrderPlaced", Instant.now(), UUID.randomUUID().toString());
        OrderPlaced event = new OrderPlaced(
                envelope, orderId, UUID.randomUUID(),
                new GeoPoint(-34.4, 150.8), new GeoPoint(-34.5, 150.9),
                "STANDARD", Instant.now().plusSeconds(3600));

        streamBridge.send(Topics.ORDER_PLACED, event);
        streamBridge.send(Topics.ORDER_PLACED, event); // duplicate delivery, same eventId

        awaitDecisionPresent(orderId);
        Thread.sleep(3000); // grace window for the duplicate to be deduped, not just late

        long decisionsForOrder = assignmentDecisionRepository.findAll().stream()
                .filter(d -> d.getOrderId().equals(orderId))
                .count();
        assertEquals(1, decisionsForOrder);

        long assignmentsForOrder = assignmentRepository.findAll().stream()
                .filter(a -> a.getOrderId().equals(orderId))
                .count();
        assertEquals(1, assignmentsForOrder);
    }

    private void registerAvailableCourierAt(UUID courierId, double latitude, double longitude) throws InterruptedException {
        EventEnvelope registeredEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierRegistered", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_REGISTERED,
                new CourierRegistered(registeredEnvelope, courierId, "VAN", 5, "ZONE_A"));
        await(() -> couriers.existsById(courierId));

        EventEnvelope availabilityEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierAvailabilityChanged", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_AVAILABILITY_CHANGED,
                new CourierAvailabilityChanged(availabilityEnvelope, courierId, true));

        EventEnvelope locationEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierLocationUpdated", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_LOCATION_UPDATED,
                new CourierLocationUpdated(locationEnvelope, courierId, new GeoPoint(latitude, longitude), Instant.now()));

        await(() -> couriers.findById(courierId)
                .map(c -> c.isAvailable() && c.getLatitude() != null).orElse(false));
    }

    private void publishOrderPlaced(UUID orderId, double pickupLatitude, double pickupLongitude) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), "OrderPlaced", Instant.now(), UUID.randomUUID().toString());
        OrderPlaced event = new OrderPlaced(
                envelope, orderId, UUID.randomUUID(),
                new GeoPoint(pickupLatitude, pickupLongitude), new GeoPoint(-34.5, 150.9),
                "STANDARD", Instant.now().plusSeconds(3600));
        streamBridge.send(Topics.ORDER_PLACED, event);
    }

    private void awaitDecisionPresent(UUID orderId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (assignmentDecisionRepository.findByOrderId(orderId).isPresent()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for an assignment_decisions row for order " + orderId);
    }

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for Kafka projection");
    }

    private void assertPublished(String topic, UUID orderId) {
        Map<String, Object> properties = Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", "verify-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest", "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.value().contains(orderId.toString())) return;
                }
            }
        }
        throw new AssertionError("No event on " + topic + " for " + orderId);
    }
}
