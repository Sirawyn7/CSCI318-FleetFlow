package com.fleetflow.dispatch.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.contracts.*;
import com.fleetflow.contracts.dispatch.OrderReassigned;
import com.fleetflow.contracts.fleet.CourierAvailabilityChanged;
import com.fleetflow.contracts.tracking.*;
import com.fleetflow.dispatch.application.*;
import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.infrastructure.persistence.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@org.springframework.test.annotation.DirtiesContext
class DeliveryExceptionIntegrationTest {
    @Container static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:fr9-integration;DB_CLOSE_DELAY=-1");
    }
    @Autowired AssignmentRepository assignments;
    @Autowired CourierAvailabilityViewRepository couriers;
    @Autowired StreamBridge bridge;
    @Autowired DeliveryExceptionService service;
    @MockitoBean DispatchAgent agent;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private UUID courier() {
        var c = new CourierAvailabilityView(UUID.randomUUID(), "VAN", 5, "ZONE_A", true, Instant.now());
        c.updateLocation(-34.4, 150.8, Instant.now()); return couriers.save(c).getCourierId();
    }
    private EventEnvelope envelope(String type) { return new EventEnvelope(UUID.randomUUID(), type, Instant.now(), "fr9-test"); }

    @Test void offlineAndSlaTriggersPublishReassignmentAndFurtherRiskEscalates() throws Exception {
        UUID old = courier(), replacement = courier(), delivery = UUID.randomUUID();
        var a = Assignment.create(UUID.randomUUID(), old, Instant.now().minusSeconds(30));
        a.rememberDestination(-34.5, 150.9, Instant.now().plusSeconds(600), "EXPRESS"); assignments.save(a);
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "fr9-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(Topics.ORDER_REASSIGNED));
            assertTrue(bridge.send(Topics.DELIVERY_STARTED, new DeliveryStarted(envelope("DeliveryStarted"), delivery, a.getOrderId(), old)));
            await(() -> assignments.findById(a.getId()).orElseThrow().isDeliveryActive());
            assertTrue(bridge.send(Topics.COURIER_AVAILABILITY_CHANGED,
                    new CourierAvailabilityChanged(envelope("CourierAvailabilityChanged"), old, false)));
            OrderReassigned event = next(consumer, a.getOrderId());
            assertEquals(old, event.previousCourierId()); assertEquals(replacement, event.newCourierId());
            assertEquals("COURIER_OFFLINE", event.reason());
            assertTrue(bridge.send(Topics.DELIVERY_AT_RISK, new DeliveryAtRisk(envelope("DeliveryAtRisk"),
                    delivery, a.getOrderId(), replacement, "SLA_BREACH")));
            await(() -> assignments.findById(a.getId()).orElseThrow().getPendingException() != null);
            var attempted = assignments.findById(a.getId()).orElseThrow().getExceptionAttemptedAt();
            service.escalateDue(a.getOrderId(), attempted.plusSeconds(599)); assertTrue(service.operatorQueue().isEmpty());
            service.escalateDue(a.getOrderId(), attempted.plusSeconds(600)); assertEquals(1, service.operatorQueue().size());
            assertTrue(consumer.poll(Duration.ofSeconds(2)).isEmpty());

            UUID other = courier();
            var second = Assignment.create(UUID.randomUUID(), replacement, Instant.now().minusSeconds(30));
            second.rememberDestination(-34.5, 150.9, Instant.now().plusSeconds(600), "EXPRESS");
            second.startDelivery(UUID.randomUUID()); assignments.save(second);
            assertTrue(bridge.send(Topics.DELIVERY_AT_RISK, new DeliveryAtRisk(envelope("DeliveryAtRisk"),
                    second.getDeliveryId(), second.getOrderId(), replacement, "SLA_BREACH")));
            assertEquals(other, next(consumer, second.getOrderId()).newCourierId());
        }
    }
    private OrderReassigned next(KafkaConsumer<String, String> consumer, UUID order) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < end) for (var record : consumer.poll(Duration.ofMillis(250))) {
            var event = mapper.readValue(record.value(), OrderReassigned.class);
            if (order.equals(event.orderId())) return event;
        }
        throw new AssertionError("OrderReassigned not received");
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(50);
        assertTrue(condition.getAsBoolean());
    }
}
