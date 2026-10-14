package com.fleetflow.order.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.contracts.Topics;
import com.fleetflow.order.application.OrderApplicationService;
import com.fleetflow.order.domain.Address;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -Dkafka.integration=true; Docker must be running. Never mocks the broker. */
@EnabledIfSystemProperty(named = "kafka.integration", matches = "true")
@Testcontainers
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:order-kafka;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"})
class OrderKafkaIntegrationTest {
    @Container static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");
    @DynamicPropertySource static void broker(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
    }
    @Autowired OrderApplicationService service;
    @Autowired ObjectMapper mapper;

    @Test void publishesPlacedAndCancelledContractsToConfiguredTopics() throws Exception {
        var config = new HashMap<String, Object>();
        config.put("bootstrap.servers", kafka.getBootstrapServers());
        config.put("group.id", "order-test-" + UUID.randomUUID());
        config.put("auto.offset.reset", "earliest");
        config.put("enable.auto.commit", false);
        try (var consumer = new KafkaConsumer<String, String>(config, new StringDeserializer(), new StringDeserializer())) {
            var address = new Address("1 Main St", "Wollongong", "NSW", "2500", -34.4, 150.8);
            var order = service.createOrder(UUID.randomUUID(), address, address, "Books", "STANDARD", Instant.now().plusSeconds(3600));
            service.cancelOrder(order.getId());
            consumer.subscribe(List.of(Topics.ORDER_PLACED, Topics.ORDER_CANCELLED));
            var received = new HashMap<String, com.fasterxml.jackson.databind.JsonNode>();
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (received.size() < 2 && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    var event = mapper.readTree(record.value());
                    if (order.getId().toString().equals(event.path("orderId").asText())) received.put(record.topic(), event);
                }
            }
            assertEquals(Set.of(Topics.ORDER_PLACED, Topics.ORDER_CANCELLED), received.keySet());
            assertEquals(order.getCustomerId().toString(), received.get(Topics.ORDER_PLACED).path("customerId").asText());
            assertEquals("STANDARD", received.get(Topics.ORDER_PLACED).path("serviceLevel").asText());
            assertEquals("Cancelled by customer", received.get(Topics.ORDER_CANCELLED).path("reason").asText());
            assertEquals(2, service.getOrder(order.getId()).orElseThrow().getStatusHistory().size());
        }
    }

    @Autowired org.springframework.cloud.stream.function.StreamBridge bridge;

    @Test void consumesLifecycleEventsFromDispatchAndTrackingTopics() throws Exception {
        var address = new Address("1 Main St", "Wollongong", "NSW", "2500", -34.4, 150.8);
        var delivered = service.createOrder(UUID.randomUUID(), address, address, "Books", "STANDARD", Instant.now().plusSeconds(3600));
        var failed = service.createOrder(UUID.randomUUID(), address, address, "Books", "STANDARD", Instant.now().plusSeconds(3600));
        assertTrue(bridge.send(Topics.ORDER_ASSIGNED, new com.fleetflow.contracts.dispatch.OrderAssigned(
                envelope("OrderAssigned"), delivered.getId(), UUID.randomUUID(), UUID.randomUUID())));
        awaitStatus(delivered.getId(), com.fleetflow.order.domain.OrderStatus.ASSIGNED);
        assertTrue(bridge.send(Topics.DELIVERY_STARTED, new com.fleetflow.contracts.tracking.DeliveryStarted(
                envelope("DeliveryStarted"), UUID.randomUUID(), delivered.getId(), UUID.randomUUID())));
        awaitStatus(delivered.getId(), com.fleetflow.order.domain.OrderStatus.IN_TRANSIT);
        assertTrue(bridge.send(Topics.DELIVERY_COMPLETED, new com.fleetflow.contracts.tracking.DeliveryCompleted(
                envelope("DeliveryCompleted"), UUID.randomUUID(), delivered.getId(), UUID.randomUUID(), Instant.now())));
        awaitStatus(delivered.getId(), com.fleetflow.order.domain.OrderStatus.DELIVERED);
        assertTrue(bridge.send(Topics.ASSIGNMENT_FAILED, new com.fleetflow.contracts.dispatch.AssignmentFailed(
                envelope("AssignmentFailed"), failed.getId(), "No eligible courier")));
        awaitStatus(failed.getId(), com.fleetflow.order.domain.OrderStatus.FAILED);
        assertEquals(4, service.getOrder(delivered.getId()).orElseThrow().getStatusHistory().size());
    }

    private static com.fleetflow.contracts.EventEnvelope envelope(String type) {
        return new com.fleetflow.contracts.EventEnvelope(UUID.randomUUID(), type, Instant.now(), "integration-test");
    }

    private void awaitStatus(UUID orderId, com.fleetflow.order.domain.OrderStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            if (service.getOrder(orderId).orElseThrow().getStatus() == expected) return;
            Thread.sleep(200);
        }
        fail("Order " + orderId + " did not reach " + expected);
    }
}
