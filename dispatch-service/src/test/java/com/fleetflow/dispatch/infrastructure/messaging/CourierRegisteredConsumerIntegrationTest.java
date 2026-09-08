package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityView;
import com.fleetflow.dispatch.infrastructure.persistence.CourierAvailabilityViewRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the whole consume path -- translate, idempotency check, projection write --
 * against a real Kafka broker (Testcontainers), not a mocked binder. Simulates Fleet's
 * publish via StreamBridge.send(Topics.COURIER_REGISTERED, event) -- a dynamic
 * destination send to the same topic Fleet actually publishes to -- rather than booting
 * Fleet's own Spring context, so this test stays self-contained to dispatch-service.
 */
@SpringBootTest
@Testcontainers
class CourierRegisteredConsumerIntegrationTest {

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dispatch-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private CourierAvailabilityViewRepository availabilityRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void duplicateDeliveryOfTheSameEventProjectsExactlyOnce() throws InterruptedException {
        UUID courierId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), "CourierRegistered", Instant.now(), UUID.randomUUID().toString());
        CourierRegistered event = new CourierRegistered(envelope, courierId, "VAN", 20, "ZONE_A");

        streamBridge.send(Topics.COURIER_REGISTERED, event);
        streamBridge.send(Topics.COURIER_REGISTERED, event); // duplicate delivery, same eventId

        awaitProcessedEventCount(1);

        // Grace window: give the second (duplicate) message a chance to arrive and be
        // deduped before asserting the counts haven't grown -- otherwise this could pass
        // merely because the duplicate hadn't been consumed yet, not because it was
        // actually deduped.
        Thread.sleep(3000);

        assertEquals(1, availabilityRepository.count());
        assertEquals(1, processedEventRepository.count());

        CourierAvailabilityView view = availabilityRepository.findById(courierId).orElseThrow();
        assertTrue(availabilityRepository.findById(courierId).isPresent());
        assertEquals("VAN", view.getVehicleType());
        assertEquals(20, view.getCapacity());
        assertEquals("ZONE_A", view.getOperatingZone());
    }

    private void awaitProcessedEventCount(long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (processedEventRepository.count() == expected) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for processed_events row count to reach " + expected);
    }
}
