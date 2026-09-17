package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.fleet.CourierAvailabilityChanged;
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
import org.testcontainers.shaded.org.yaml.snakeyaml.events.Event;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class CourierAvailabilityChangedConsumerIntegrationTest {
    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dispatch-availability-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private CourierAvailabilityViewRepository availabilityRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void courierAvailabilityChangedUpdatesOnlyTheAvailableFlag() throws InterruptedException {
        UUID courierId = UUID.randomUUID();

        EventEnvelope registeredEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierRegistered", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_REGISTERED,
                new CourierRegistered(registeredEnvelope, courierId, "VAN", 20, "ZONE_A"));

        awaitViewPresent(courierId);

        EventEnvelope availabilityEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierAvailabilityChanged", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_AVAILABILITY_CHANGED,
                new CourierAvailabilityChanged(availabilityEnvelope, courierId, true));

        awaitProcessedEvent(availabilityEnvelope.eventId());

        CourierAvailabilityView view = availabilityRepository.findById(courierId).orElseThrow();
        assertTrue(view.isAvailable());
        assertEquals("VAN", view.getVehicleType());
        assertEquals(20, view.getCapacity());
        assertEquals("ZONE_A", view.getOperatingZone());
    }

    @Test
    void courierAvailabilityChangedArrivingBeforeRegistrationIsRetriedSuccessfullyOnRedelivery()
            throws InterruptedException {
        UUID courierId = UUID.randomUUID();
        UUID availabilityEventId = UUID.randomUUID();

        EventEnvelope earlyAvailabilityEnvelope = new EventEnvelope(
                availabilityEventId, "CourierAvailabilityChanged", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_AVAILABILITY_CHANGED,
                new CourierAvailabilityChanged(earlyAvailabilityEnvelope, courierId, true));

        Thread.sleep(3000);
        assertTrue(availabilityRepository.findById(courierId).isEmpty());
        assertTrue(processedEventRepository.findById(availabilityEventId).isEmpty());

        EventEnvelope registeredEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierRegistered", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_REGISTERED,
                new CourierRegistered(registeredEnvelope, courierId, "VAN", 20, "ZONE_A"));
        awaitViewPresent(courierId);

        streamBridge.send(Topics.COURIER_AVAILABILITY_CHANGED,
                new CourierAvailabilityChanged(earlyAvailabilityEnvelope, courierId, true));

        awaitAvailabilityBecomes(courierId, true);
        assertTrue(processedEventRepository.findById(availabilityEventId).isPresent());
    }

    private void awaitAvailabilityBecomes(UUID courierId, boolean expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            boolean matched = availabilityRepository.findById(courierId).map(view -> view.isAvailable() == expected).orElse(false);
            if (matched) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for courier_availability_view.available to become " + expected);
    }

    private void awaitViewPresent(UUID courierId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (availabilityRepository.findById(courierId).isPresent()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for courier_availability_view row to appear");
    }

    private void awaitProcessedEvent(UUID eventId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (processedEventRepository.existsById(eventId)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for processed event " + eventId);
    }
}
