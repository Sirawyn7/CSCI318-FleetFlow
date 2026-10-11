package com.fleetflow.notification.infrastructure.messaging;

import com.fleetflow.contracts.*;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.dispatch.OrderReassigned;
import com.fleetflow.contracts.dispatch.AssignmentFailed;
import com.fleetflow.contracts.tracking.*;
import com.fleetflow.notification.application.*;
import com.fleetflow.notification.infrastructure.persistence.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"notification.worker.enabled=false", "notification.agent.api-key=", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Testcontainers(disabledWithoutDocker = true)
class NotificationKafkaIntegrationTest {
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:notification-kafka;DB_CLOSE_DELAY=-1");
    }
    @Autowired StreamBridge bridge;
    @Autowired ProcessedEventRepository processed;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationDeliveryService delivery;
    @Autowired NotificationService service;
    @Autowired ObjectMapper mapper;
    @MockitoBean DeliveryDataPort data;

    @Test void sevenLifecycleBindingsDeduplicateAndPublishRealAuditEvents() throws Exception {
        UUID order = UUID.randomUUID(), customer = UUID.randomUUID(), courier = UUID.randomUUID(), deliveryId = UUID.randomUUID();
        when(data.order(order)).thenReturn(new DeliveryData.Order(order, customer, "IN_TRANSIT", null));
        var near = new DeliveryNearDestination(envelope("DeliveryNearDestination"), deliveryId, 200);
        assertTrue(bridge.send(Topics.DELIVERY_NEAR_DESTINATION, near)); awaitEvent(near.envelope().eventId());
        UUID nearId = notifications.findAll().getFirst().getId();
        delivery.deliver(nearId); assertNull(notifications.findById(nearId).orElseThrow().getSentAt());
        var assigned = new OrderAssigned(envelope("OrderAssigned"), order, courier, UUID.randomUUID());
        var started = new DeliveryStarted(envelope("DeliveryStarted"), deliveryId, order, courier);
        var risk = new DeliveryAtRisk(envelope("DeliveryAtRisk"), deliveryId, order, courier, "Stationary");
        var complete = new DeliveryCompleted(envelope("DeliveryCompleted"), deliveryId, order, courier, Instant.now());
        assertTrue(bridge.send(Topics.ORDER_ASSIGNED, assigned)); awaitEvent(assigned.envelope().eventId());
        assertTrue(bridge.send(Topics.DELIVERY_STARTED, started)); awaitEvent(started.envelope().eventId());
        assertTrue(bridge.send(Topics.DELIVERY_AT_RISK, risk)); awaitEvent(risk.envelope().eventId());
        assertTrue(bridge.send(Topics.DELIVERY_COMPLETED, complete)); awaitEvent(complete.envelope().eventId());
        var reassigned = new OrderReassigned(envelope("OrderReassigned"), order, courier,
                UUID.randomUUID(), UUID.randomUUID(), "Courier offline");
        var failed = new AssignmentFailed(envelope("AssignmentFailed"), order, "No available courier");
        assertTrue(bridge.send(Topics.ORDER_REASSIGNED, reassigned)); awaitEvent(reassigned.envelope().eventId());
        assertTrue(bridge.send(Topics.ASSIGNMENT_FAILED, failed)); awaitEvent(failed.envelope().eventId());
        assertTrue(bridge.send(Topics.ORDER_REASSIGNED, reassigned));
        assertTrue(bridge.send(Topics.ASSIGNMENT_FAILED, failed));
        assertTrue(bridge.send(Topics.ORDER_ASSIGNED, assigned));
        var marker = new OrderAssigned(envelope("OrderAssigned"), order, courier, UUID.randomUUID());
        assertTrue(bridge.send(Topics.ORDER_ASSIGNED, marker)); awaitEvent(marker.envelope().eventId());
        assertEquals(8, notifications.count()); assertEquals(8, processed.count());
        notifications.findAll().forEach(n -> delivery.deliver(n.getId()));
        assertEquals(8, service.inbox(customer, 0, 20).size());
        assertTrue(service.inbox(customer, 0, 20).stream().anyMatch(n -> n.message().contains("Courier offline")));
        assertTrue(service.inbox(customer, 0, 20).stream().anyMatch(n -> n.message().contains("No available courier")));
        notifications.findAll().forEach(n -> delivery.publishAudit(n.getId()));
        var config = new HashMap<String, Object>();
        config.put("bootstrap.servers", KAFKA.getBootstrapServers()); config.put("group.id", "audit-test-" + UUID.randomUUID());
        config.put("auto.offset.reset", "earliest"); config.put("key.deserializer", StringDeserializer.class);
        config.put("value.deserializer", StringDeserializer.class);
        Set<String> auditIds = new HashSet<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(Topics.NOTIFICATION_SENT));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (auditIds.size() < 8 && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    var json = mapper.readTree(record.value());
                    assertEquals(customer.toString(), json.get("recipientId").asText());
                    assertEquals("IN_APP", json.get("channel").asText());
                    assertEquals(json.get("notificationId").asText(), json.get("envelope").get("eventId").asText());
                    auditIds.add(json.get("notificationId").asText());
                }
            }
        }
        assertEquals(8, auditIds.size());
    }
    private EventEnvelope envelope(String type) { return new EventEnvelope(UUID.randomUUID(), type, Instant.now(), "uc8-test"); }
    private void awaitEvent(UUID id) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (processed.existsById(id)) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for " + id);
    }
}
