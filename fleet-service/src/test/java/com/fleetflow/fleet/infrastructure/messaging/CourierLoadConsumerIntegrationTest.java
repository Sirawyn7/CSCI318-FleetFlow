package com.fleetflow.fleet.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.tracking.DeliveryCompleted;
import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.VehicleType;
import com.fleetflow.fleet.infrastructure.persistence.CourierJpaRepository;
import com.fleetflow.fleet.infrastructure.persistence.ProcessedEventRepository;
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
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CourierLoadConsumerIntegrationTest {
    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:fleet-kafka;DB_CLOSE_DELAY=-1");
    }

    @Autowired StreamBridge bridge;
    @Autowired CourierJpaRepository couriers;
    @Autowired ProcessedEventRepository processed;

    @Test void realKafkaProcessesAssignmentsCompletionsAndDuplicates() throws Exception {
        UUID courierId = couriers.save(Courier.register(VehicleType.VAN, 3, "A")).getId();
        UUID orderId = UUID.randomUUID();
        OrderAssigned assigned = new OrderAssigned(envelope("OrderAssigned"), orderId, courierId, UUID.randomUUID());
        assertTrue(bridge.send("fleetflow.dispatch.order-assigned", assigned));
        awaitEvent(assigned.envelope().eventId());
        assertEquals(1, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
        assertTrue(bridge.send("fleetflow.dispatch.order-assigned", assigned));
        // A later marker on the same single-partition topic proves the duplicate was consumed.
        OrderAssigned marker = new OrderAssigned(envelope("OrderAssigned"), orderId, courierId, UUID.randomUUID());
        assertTrue(bridge.send("fleetflow.dispatch.order-assigned", marker));
        awaitEvent(marker.envelope().eventId());
        assertEquals(1, couriers.findById(courierId).orElseThrow().getActiveOrderCount());

        DeliveryCompleted completed = new DeliveryCompleted(envelope("DeliveryCompleted"),
                UUID.randomUUID(), orderId, courierId, Instant.now());
        assertTrue(bridge.send("fleetflow.tracking.delivery-completed", completed));
        awaitEvent(completed.envelope().eventId());
        assertTrue(bridge.send("fleetflow.tracking.delivery-completed", completed));
        DeliveryCompleted completionMarker = new DeliveryCompleted(envelope("DeliveryCompleted"),
                completed.deliveryId(), orderId, courierId, Instant.now());
        assertTrue(bridge.send("fleetflow.tracking.delivery-completed", completionMarker));
        awaitEvent(completionMarker.envelope().eventId());
        assertEquals(0, couriers.findById(courierId).orElseThrow().getActiveOrderCount());
        assertEquals(4, processed.count());
    }

    private EventEnvelope envelope(String type) {
        return new EventEnvelope(UUID.randomUUID(), type, Instant.now(), UUID.randomUUID().toString());
    }

    private void awaitEvent(UUID id) throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (processed.existsById(id)) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for event " + id);
    }
}
