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
}
