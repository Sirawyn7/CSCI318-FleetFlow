package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.order.OrderCancelled;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderViewRepository;
import com.fleetflow.dispatch.infrastructure.persistence.ProcessedEventRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the whole consume path -- translate, idempotency check, projection write/delete
 * -- against a real Kafka broker (Testcontainers), not a mocked binder. Simulates
 * Order's publish via StreamBridge.send(Topics.ORDER_PLACED/ORDER_CANCELLED, event) --
 * dynamic destination sends to the same topics Order actually publishes to -- rather
 * than booting Order's own Spring context, so this test stays self-contained to
 * dispatch-service (same technique as CourierRegisteredConsumerIntegrationTest).
 */
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class OpenOrdersConsumerIntegrationTest {

    // withKraft() requires Confluent Platform 7.0.0+ (the default constructor's image,
    // 5.4.3, predates KRaft support and throws IllegalArgumentException) -- pin an
    // explicit KRaft-capable tag rather than relying on the constructor's default.
    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        // The default application.yml points at the file-based dev database
        // (./data/dispatchdb), which persists across runs -- reusing it here would let
        // rows from a previous test run leak into this test's row-count assertions.
        // An in-memory instance keeps this test isolated and repeatable.
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dispatch-open-orders-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private OpenOrderViewRepository openOrderViewRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void orderPlacedInsertsRowThenOrderCancelledRemovesIt() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        EventEnvelope placedEnvelope = new EventEnvelope(
                UUID.randomUUID(), "OrderPlaced", Instant.now(), UUID.randomUUID().toString());
        OrderPlaced placed = new OrderPlaced(
                placedEnvelope, orderId, customerId,
                new GeoPoint(-34.4, 150.8), new GeoPoint(-34.5, 150.9),
                "STANDARD", Instant.now().plusSeconds(3600));

        // Duplicate delivery of the same OrderPlaced event -- must project exactly once.
        streamBridge.send(Topics.ORDER_PLACED, placed);
        streamBridge.send(Topics.ORDER_PLACED, placed);

        awaitOpenOrderViewCount(1);
        Thread.sleep(2000); // grace window for the duplicate to be deduped, not just late

        assertEquals(1, openOrderViewRepository.count());
        OpenOrderView view = openOrderViewRepository.findById(orderId).orElseThrow();
        assertEquals(customerId, view.getCustomerId());
        assertEquals("STANDARD", view.getServiceLevel());

        EventEnvelope cancelledEnvelope = new EventEnvelope(
                UUID.randomUUID(), "OrderCancelled", Instant.now(), UUID.randomUUID().toString());
        OrderCancelled cancelled = new OrderCancelled(cancelledEnvelope, orderId, "customer requested");

        // Duplicate delivery of the same OrderCancelled event -- the second delivery
        // must find the row already gone and not error.
        streamBridge.send(Topics.ORDER_CANCELLED, cancelled);
        streamBridge.send(Topics.ORDER_CANCELLED, cancelled);

        awaitOpenOrderViewCount(0);
        Thread.sleep(2000);

        assertEquals(0, openOrderViewRepository.count());
        assertFalse(openOrderViewRepository.findById(orderId).isPresent());
        assertTrue(processedEventRepository.existsById(placedEnvelope.eventId()));
        assertTrue(processedEventRepository.existsById(cancelledEnvelope.eventId()));
    }

    private void awaitOpenOrderViewCount(long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (openOrderViewRepository.count() == expected) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for open_orders_view row count to reach " + expected);
    }
}
