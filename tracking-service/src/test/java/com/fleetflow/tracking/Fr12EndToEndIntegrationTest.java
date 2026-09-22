package com.fleetflow.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.dispatch.OrderAssigned;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.fleet.CourierRegistered;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.contracts.tracking.DeliveryArrived;
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

/**
 * FR12 end to end against a real Kafka broker: same bootstrap path as
 * Fr11EndToEndIntegrationTest (OrderPlaced + CourierRegistered -> OrderAssigned ->
 * Delivery bootstrap), then a location ping placed well within the arrival threshold
 * so both DeliveryNearDestination and DeliveryArrived fire from the same ping --
 * exercising both of FR12's two separate topology functions (see
 * GeofenceDetectionTopology's Javadoc for why they're separate) in one test.
 */

@SpringBootTest
@Testcontainers
public class Fr12EndToEndIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:tracking-fr12-it;DB_CLOSE_DELAY=-1");
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

    private KafkaConsumer<String, byte[]> nearConsumer;
    private KafkaConsumer<String, byte[]> arrivedConsumer;

    @BeforeEach
    void setUpConsumers() {
        nearConsumer = subscribedConsumer(Topics.DELIVERY_NEAR_DESTINATION);
        arrivedConsumer = subscribedConsumer(Topics.DELIVERY_ARRIVED);
    }

    private KafkaConsumer<String, byte[]> subscribedConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "fr12-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    @AfterEach
    void tearDownConsumers() {
        nearConsumer.close();
        arrivedConsumer.close();
    }

    @Test
    void bootstrapThroughTopologyProducesBothGeofenceEvents() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        GeoPoint destination = new GeoPoint(-33.8688, 151.2093);

        streamBridge.send(Topics.ORDER_PLACED, new OrderPlaced(
                newEnvelope("OrderPlaced"), orderId, UUID.randomUUID(),
                new GeoPoint(-33.87, 151.20), destination, "STANDARD", Instant.now().plusSeconds(3600)));

        streamBridge.send(Topics.COURIER_REGISTERED, new CourierRegistered(
                newEnvelope("CourierRegistered"), courierId, "VAN", 20, "ZONE_A"));

        awaitDestinationProjected(orderId);
        awaitVehicleTypeProjected(courierId);

        streamBridge.send(Topics.ORDER_ASSIGNED, new OrderAssigned(
                newEnvelope("OrderAssigned"), orderId, courierId, UUID.randomUUID()));

        UUID deliveryId = awaitDeliveryBootstrapped(orderId);

        //Settle period for the Streams topology's KTables to materialize the
        // active-delivery snapshot before the location ping arrives -- same reasoning
        //as Fr11EndToEndIntegrationTest
        Thread.sleep(5000);

        // A few metres from the destination -- well within both the 500m near
        // threshold and the 50m arrival threshold, so one ping should fire both
        // events
        Message<CourierLocationUpdated> locationMessage = MessageBuilder.withPayload(
                new CourierLocationUpdated(newEnvelope("CourierLocationUpdated"), courierId,
                        new GeoPoint(-33.8689, 151.2094), Instant.now()))
                .setHeader(KafkaHeaders.KEY, courierId.toString())
                .build();
        streamBridge.send(Topics.COURIER_LOCATION_UPDATED, locationMessage);

        DeliveryNearDestination near = awaitEvent(nearConsumer, DeliveryNearDestination.class,
                Topics.DELIVERY_NEAR_DESTINATION);
        DeliveryArrived arrived = awaitEvent(arrivedConsumer, DeliveryArrived.class, Topics.DELIVERY_ARRIVED);

        assertEquals(deliveryId, near.deliveryId());
        assertTrue(near.distanceMetersRemaining() <= 50.0);
        assertEquals(deliveryId, arrived.deliveryId());
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

