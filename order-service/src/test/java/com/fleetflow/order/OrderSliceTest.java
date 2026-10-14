package com.fleetflow.order;

import com.fleetflow.order.application.OrderApplicationService;
import com.fleetflow.order.application.OrderEventPort;
import com.fleetflow.order.domain.*;
import com.fleetflow.order.infrastructure.OrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No consumer bindings without a broker; tests invoke the lifecycle consumer beans directly.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:order-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.cloud.function.definition="})
@AutoConfigureMockMvc
class OrderSliceTest {
    @Autowired OrderApplicationService service;
    @Autowired OrderJpaRepository repository;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean OrderEventPort events;
    @BeforeEach void clean() { repository.deleteAll(); }

    static Address address() { return new Address("1 Main St", "Wollongong", "NSW", "2500", -34.4, 150.8); }
    Order create() { return service.createOrder(UUID.randomUUID(), address(), address(), "Books", "STANDARD", Instant.now().plusSeconds(3600)); }

    @Test void creationAndCancellationRoundTripWithHistory() {
        var order = create();
        var loaded = service.getOrder(order.getId()).orElseThrow();
        assertEquals(order.getPickupAddress(), loaded.getPickupAddress());
        assertEquals(OrderStatus.PLACED, loaded.getStatus());
        assertEquals(1, loaded.getStatusHistory().size());
        assertEquals(java.util.List.of(new OrderItem("Books", 1)), loaded.getItems());
        service.cancelOrder(order.getId());
        loaded = service.getOrder(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, loaded.getStatus());
        assertEquals(2, loaded.getStatusHistory().size());
        assertEquals(OrderStatus.PLACED, loaded.getStatusHistory().getFirst().status());
        assertEquals(OrderStatus.CANCELLED, loaded.getStatusHistory().getLast().status());
        assertNotNull(loaded.getStatusHistory().getLast().changedAt());
        verify(events).publishOrderPlaced(any());
        verify(events).publishOrderCancelled(any());
    }
    @Test void failedPublishRollsBackFlushedCreationAndRetryCreatesOneRow() {
        doAnswer(invocation -> { repository.flush(); throw new IllegalStateException("broker failed"); })
                .when(events).publishOrderPlaced(any());
        assertThrows(IllegalStateException.class, this::create);
        assertEquals(0, repository.count());
        doNothing().when(events).publishOrderPlaced(any());
        create();
        assertEquals(1, repository.count());
    }
    @Test void failedCancellationRollsBackStatusAndHistory() {
        var order = create();
        doAnswer(invocation -> { repository.flush(); throw new IllegalStateException("broker failed"); })
                .when(events).publishOrderCancelled(any());
        assertThrows(IllegalStateException.class, () -> service.cancelOrder(order.getId()));
        var loaded = service.getOrder(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PLACED, loaded.getStatus());
        assertEquals(1, loaded.getStatusHistory().size());
        assertEquals(java.util.List.of(new OrderItem("Books", 1)), loaded.getItems());
    }
    @Test void cancellationEndpointReturnsDtoAndConflictForRepeatedCancellation() throws Exception {
        var order = create();
        mvc.perform(post("/orders/{id}/cancel", order.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.statusHistory.length()").value(2));
        mvc.perform(post("/orders/{id}/cancel", order.getId())).andExpect(status().isConflict());
        mvc.perform(get("/orders/{id}", order.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.pickupAddress.street").value("1 Main St"));
        verify(events, times(1)).publishOrderCancelled(any());
    }
    @Test void missingOrdersReturn404AndDoNotPublish() throws Exception {
        mvc.perform(get("/orders/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post("/orders/{id}/cancel", UUID.randomUUID())).andExpect(status().isNotFound());
        verifyNoInteractions(events);
    }
    @Test void itemsAreOwnedPersistedAndReturnedByTheOrder() throws Exception {
        var items = new java.util.ArrayList<>(java.util.List.of(new OrderItem("Book", 2), new OrderItem("Pen", 3)));
        var order = service.createOrder(UUID.randomUUID(), address(), address(), "Stationery parcel", "STANDARD",
                Instant.now().plusSeconds(3600), items);
        items.clear();
        var loaded = service.getOrder(order.getId()).orElseThrow();
        assertEquals(java.util.List.of(new OrderItem("Book", 2), new OrderItem("Pen", 3)), loaded.getItems());
        assertThrows(UnsupportedOperationException.class, () -> loaded.getItems().clear());
        assertEquals(2, jdbc.queryForObject("select count(*) from order_items where order_id = ?", Integer.class, order.getId()));
        mvc.perform(get("/orders/{id}", order.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].description").value("Book"))
            .andExpect(jsonPath("$.items[1].quantity").value(3));
        mvc.perform(post("/orders/{id}/cancel", order.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test void databaseConstraintFailureDoesNotPublish() {
        jdbc.execute("alter table orders add constraint review_service_level check (service_level <> 'REJECTED')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                service.createOrder(UUID.randomUUID(), address(), address(), "Books", "REJECTED", Instant.now().plusSeconds(3600)));
            assertEquals(0, repository.count());
            verifyNoInteractions(events);
        } finally {
            jdbc.execute("alter table orders drop constraint review_service_level");
        }
    }

    @Test void concurrentCancellationPublishesOnlyOnce() throws Exception {
        var order = create();
        var firstPublishing = new java.util.concurrent.CountDownLatch(1);
        var finishFirst = new java.util.concurrent.CountDownLatch(1);
        var secondStarted = new java.util.concurrent.CountDownLatch(1);
        var publishCount = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (publishCount.incrementAndGet() == 1) {
                firstPublishing.countDown();
                if (!finishFirst.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Test timed out");
            }
            return null;
        }).when(events).publishOrderCancelled(any());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.cancelOrder(order.getId()));
            assertTrue(firstPublishing.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return service.cancelOrder(order.getId());
            });
            assertTrue(secondStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> second.get(250, java.util.concurrent.TimeUnit.MILLISECONDS));
            finishFirst.countDown();
            assertEquals(OrderStatus.CANCELLED, first.get(5, java.util.concurrent.TimeUnit.SECONDS).orElseThrow().getStatus());
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> second.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(OrderCancellationException.class, failure.getCause());
            verify(events, times(1)).publishOrderCancelled(any());
            assertEquals(2, service.getOrder(order.getId()).orElseThrow().getStatusHistory().size());
        } finally {
            finishFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test void createEndpointReturns201AndRejectsInvalidInput() throws Exception {
        String request = """
            {"customerId":"%s", "pickupStreet":"1 Main St", "pickupSuburb":"Wollongong",
             "pickupState":"NSW", "pickupPostcode":"2500", "pickupLatitude":-34.4, "pickupLongitude":150.8,
             "dropoffStreet":"2 Main St", "dropoffSuburb":"Wollongong", "dropoffState":"NSW",
             "dropoffPostcode":"2500", "dropoffLatitude":-34.5, "dropoffLongitude":150.9,
             "packageDetails":"Books", "serviceLevel":"STANDARD", "requestedDeliveryBy":"2030-01-01T00:00:00Z"}
            """.formatted(UUID.randomUUID());
        mvc.perform(post("/orders").contentType("application/json").content(request))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("PLACED"));
        mvc.perform(post("/orders").contentType("application/json").content(request.replace("Books", "")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/orders").contentType("application/json").content(request.replace("-34.4", "100")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/orders").contentType("application/json").content(request.replace("Books", "x".repeat(256))))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/orders").contentType("application/json").content(request.replace("1 Main St", "x".repeat(256))))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/orders").contentType("application/json")
                .content(request.replace("\"packageDetails\":", "\"items\":[],\"packageDetails\":")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/orders").contentType("application/json")
                .content(request.replace("\"packageDetails\":", "\"items\":[{\"description\":\"Book\",\"quantity\":0}],\"packageDetails\":")))
            .andExpect(status().isBadRequest());
        verify(events, times(1)).publishOrderPlaced(any());
        assertEquals(1, repository.count());
    }

    @Autowired com.fleetflow.order.infrastructure.OrderLifecycleConsumers lifecycle;

    private static com.fleetflow.contracts.EventEnvelope envelope(String type, Instant at) {
        return new com.fleetflow.contracts.EventEnvelope(UUID.randomUUID(), type, at, "test");
    }

    @Test void lifecycleEventsAdvanceStatusAndHistoryWithEventTimes() throws Exception {
        var order = create();
        var t = Instant.parse("2026-10-14T01:00:00Z");
        var assigned = new com.fleetflow.contracts.dispatch.OrderAssigned(envelope("OrderAssigned", t),
                order.getId(), UUID.randomUUID(), UUID.randomUUID());
        lifecycle.orderAssignedConsumer().accept(assigned);
        lifecycle.orderAssignedConsumer().accept(assigned);
        lifecycle.deliveryStartedConsumer().accept(new com.fleetflow.contracts.tracking.DeliveryStarted(
                envelope("DeliveryStarted", t.plusSeconds(60)), UUID.randomUUID(), order.getId(), UUID.randomUUID()));
        lifecycle.deliveryCompletedConsumer().accept(new com.fleetflow.contracts.tracking.DeliveryCompleted(
                envelope("DeliveryCompleted", t.plusSeconds(600)), UUID.randomUUID(), order.getId(),
                UUID.randomUUID(), t.plusSeconds(600)));
        mvc.perform(get("/orders/{id}", order.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DELIVERED"))
            .andExpect(jsonPath("$.statusHistory.length()").value(4))
            .andExpect(jsonPath("$.statusHistory[1].status").value("ASSIGNED"))
            .andExpect(jsonPath("$.statusHistory[1].changedAt").value("2026-10-14T01:00:00Z"))
            .andExpect(jsonPath("$.statusHistory[2].status").value("IN_TRANSIT"))
            .andExpect(jsonPath("$.statusHistory[3].status").value("DELIVERED"));
        verify(events, never()).publishOrderCancelled(any());
    }

    @Test void assignedOrdersCanNoLongerBeCancelled() throws Exception {
        var order = create();
        lifecycle.orderAssignedConsumer().accept(new com.fleetflow.contracts.dispatch.OrderAssigned(
                envelope("OrderAssigned", Instant.now()), order.getId(), UUID.randomUUID(), UUID.randomUUID()));
        mvc.perform(post("/orders/{id}/cancel", order.getId())).andExpect(status().isConflict());
        assertEquals(OrderStatus.ASSIGNED, service.getOrder(order.getId()).orElseThrow().getStatus());
        verify(events, never()).publishOrderCancelled(any());
    }

    @Test void assignmentFailureMarksOrderFailed() {
        var order = create();
        lifecycle.assignmentFailedConsumer().accept(new com.fleetflow.contracts.dispatch.AssignmentFailed(
                envelope("AssignmentFailed", Instant.now()), order.getId(), "No eligible courier"));
        var loaded = service.getOrder(order.getId()).orElseThrow();
        assertEquals(OrderStatus.FAILED, loaded.getStatus());
        assertEquals(OrderStatus.FAILED, loaded.getStatusHistory().getLast().status());
    }

    @Test void cancellationBeforeALateAssignmentStaysCancelled() {
        var order = create();
        service.cancelOrder(order.getId());
        lifecycle.orderAssignedConsumer().accept(new com.fleetflow.contracts.dispatch.OrderAssigned(
                envelope("OrderAssigned", Instant.now()), order.getId(), UUID.randomUUID(), UUID.randomUUID()));
        var loaded = service.getOrder(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, loaded.getStatus());
        assertEquals(2, loaded.getStatusHistory().size());
    }

    @Test void eventForAnUnknownOrderIsRejectedForRetry() {
        var event = new com.fleetflow.contracts.dispatch.OrderAssigned(
                envelope("OrderAssigned", Instant.now()), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> lifecycle.orderAssignedConsumer().accept(event));
    }
}
