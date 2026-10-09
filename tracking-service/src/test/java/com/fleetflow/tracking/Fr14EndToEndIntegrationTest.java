package com.fleetflow.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.contracts.Topics;
import com.fleetflow.contracts.tracking.DeliveryCompleted;
import com.fleetflow.tracking.application.DeliveryCompletionService;
import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Fr14EndToEndIntegrationTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:tracking-fr14-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired DeliveryCompletionService service;
    @Autowired DeliveryJpaRepository repository;
    @Autowired com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository couriers;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void completedProofIsStoredAndPublishedToRealKafka() throws Exception {
        UUID courier = UUID.randomUUID();
        couriers.saveAndFlush(new com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeView(courier, "VAN"));
        var d = repository.saveAndFlush(Delivery.start(UUID.randomUUID(), courier,
                new GeoCoordinate(-34, 151), 30));
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "fr14-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(Topics.DELIVERY_COMPLETED));
            service.complete(d.getId(), d.getCourierId(), "Depot to customer", "Received by Alex");
            service.complete(d.getId(), d.getCourierId(), "Depot to customer", "Received by Alex");
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            DeliveryCompleted found = null;
            while (found == null && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    var event = mapper.readValue(record.value(), DeliveryCompleted.class);
                    if (event.deliveryId().equals(d.getId())) {
                        assertNull(found, "Duplicate completion event");
                        found = event;
                    }
                }
            }
            assertNotNull(found, "DeliveryCompleted was not published");
            assertEquals(d.getOrderId(), found.orderId());
            assertEquals(d.getCourierId(), found.courierId());
            assertEquals("DeliveryCompleted", found.envelope().eventType());
            assertNotNull(found.completedAt());
            assertEquals("Received by Alex", service.get(d.getId()).getConfirmation());
            assertTrue(consumer.poll(Duration.ofSeconds(2)).isEmpty(), "Retry must not publish again");
        }
    }
}
