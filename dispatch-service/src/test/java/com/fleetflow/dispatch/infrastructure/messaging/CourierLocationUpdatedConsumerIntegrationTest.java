package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
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
 * Drives FR10's CourierLocationUpdated into courier_availability_view's location
 * fields -- the prerequisite FR7's proximity ranking needs (docs/decisions/0001,
 * deferred to FR7/FR8). Same Testcontainers shape as
 * CourierAvailabilityChangedConsumerIntegrationTest.
 */
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class CourierLocationUpdatedConsumerIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dispatch-location-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private CourierAvailabilityViewRepository availabilityRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void courierLocationUpdatedSetsLatitudeAndLongitude() throws InterruptedException {
        UUID courierId = UUID.randomUUID();

        EventEnvelope registeredEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierRegistered", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_REGISTERED,
                new CourierRegistered(registeredEnvelope, courierId, "VAN", 20, "ZONE_A"));

        awaitViewPresent(courierId);

        Instant capturedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        EventEnvelope locationEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierLocationUpdated", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_LOCATION_UPDATED,
                new CourierLocationUpdated(locationEnvelope, courierId, new GeoPoint(-34.4, 150.8), capturedAt));

        awaitProcessedEvent(locationEnvelope.eventId());

        CourierAvailabilityView view = availabilityRepository.findById(courierId).orElseThrow();
        assertEquals(-34.4, view.getLatitude());
        assertEquals(150.8, view.getLongitude());
        assertEquals(capturedAt, view.getLocationUpdatedAt());
    }

    @Test
    void courierLocationUpdatedArrivingBeforeRegistrationIsRetriedSuccessfullyOnRedelivery()
            throws InterruptedException {
        UUID courierId = UUID.randomUUID();
        UUID locationEventId = UUID.randomUUID();

        EventEnvelope earlyLocationEnvelope = new EventEnvelope(
                locationEventId, "CourierLocationUpdated", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_LOCATION_UPDATED,
                new CourierLocationUpdated(earlyLocationEnvelope, courierId, new GeoPoint(-34.4, 150.8), Instant.now()));

        Thread.sleep(3000);
        assertTrue(availabilityRepository.findById(courierId).isEmpty());
        assertTrue(processedEventRepository.findById(locationEventId).isEmpty());

        EventEnvelope registeredEnvelope = new EventEnvelope(
                UUID.randomUUID(), "CourierRegistered", Instant.now(), UUID.randomUUID().toString());
        streamBridge.send(Topics.COURIER_REGISTERED,
                new CourierRegistered(registeredEnvelope, courierId, "VAN", 20, "ZONE_A"));
        awaitViewPresent(courierId);

        streamBridge.send(Topics.COURIER_LOCATION_UPDATED,
                new CourierLocationUpdated(earlyLocationEnvelope, courierId, new GeoPoint(-34.4, 150.8), Instant.now()));

        awaitLocationPresent(courierId);
        assertTrue(processedEventRepository.findById(locationEventId).isPresent());
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

    private void awaitLocationPresent(UUID courierId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            boolean matched = availabilityRepository.findById(courierId).map(v -> v.getLatitude() != null).orElse(false);
            if (matched) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for courier_availability_view.latitude to be set");
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
