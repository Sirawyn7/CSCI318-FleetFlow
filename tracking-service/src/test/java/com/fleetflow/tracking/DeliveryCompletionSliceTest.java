package com.fleetflow.tracking;

import com.fleetflow.tracking.api.DeliveryController;
import com.fleetflow.tracking.application.DeliveryCompletionService;
import com.fleetflow.tracking.domain.Delivery;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.messaging.DeliveryCompletedEventPublisher;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.DeliveryJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DataJpaTest
@Import({DeliveryCompletionService.class, DeliveryController.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryCompletionSliceTest {
    @Autowired DeliveryCompletionService service;
    @Autowired DeliveryController controller;
    @Autowired DeliveryJpaRepository repository;
    @Autowired com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeViewRepository couriers;
    @MockitoBean DeliveryCompletedEventPublisher publisher;
    @MockitoBean ActiveDeliverySnapshotPublisher snapshots;

    private Delivery start() {
        UUID courier = UUID.randomUUID();
        couriers.saveAndFlush(new com.fleetflow.tracking.infrastructure.persistence.CourierVehicleTypeView(courier, "VAN"));
        return repository.saveAndFlush(Delivery.start(UUID.randomUUID(), courier,
                new GeoCoordinate(-34, 151), 30));
    }

    @Test
    void restCompletionPersistsProofAndHistoryAndDoesNotRepublishRetries() throws Exception {
        var d = start();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        String body = """
                {"courierId":"%s","routeSummary":"Depot to customer","confirmation":"Received by Alex"}
                """.formatted(d.getCourierId());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/deliveries/{id}/complete", d.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"))
                    .andExpect(jsonPath("$.completedAt").isNotEmpty());
        }
        mvc.perform(get("/api/deliveries/{id}", d.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmation").value("Received by Alex"));
        mvc.perform(get("/api/deliveries").param("orderId", d.getOrderId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].routeSummary").value("Depot to customer"));
        verify(publisher, times(1)).publish(any());
        verify(snapshots, times(1)).publish(any(Delivery.class));
        assertNotNull(repository.findById(d.getId()).orElseThrow().getCompletedAt());
    }

    @Test
    void concurrentRetriesPublishOnce() throws Exception {
        var d = start();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Delivery> task = () -> {
                gate.await();
                return service.complete(d.getId(), d.getCourierId(), "Route", "Proof");
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            gate.countDown();
            assertNotNull(first.get(10, java.util.concurrent.TimeUnit.SECONDS).getCompletedAt());
            assertNotNull(second.get(10, java.util.concurrent.TimeUnit.SECONDS).getCompletedAt());
        }
        verify(publisher, times(1)).publish(any());
        verify(snapshots, times(1)).publish(any(Delivery.class));
    }

    @Test
    void publicationFailureRollsBackProof() {
        var d = start();
        doThrow(new RuntimeException("broker unavailable")).when(publisher).publish(any());
        assertThrows(RuntimeException.class,
                () -> service.complete(d.getId(), d.getCourierId(), "Route", "Proof"));
        assertNull(repository.findById(d.getId()).orElseThrow().getCompletedAt());
        var order = inOrder(snapshots, publisher);
        order.verify(snapshots).publish(any(Delivery.class));
        order.verify(publisher).publish(any());
    }

    @Test
    void snapshotFailureRollsBackProofBeforeExternalPublishAndRetryPublishesOnce() {
        var d = start();
        doThrow(new IllegalStateException("snapshot unavailable")).doNothing()
                .when(snapshots).publish(any(Delivery.class));
        assertThrows(IllegalStateException.class,
                () -> service.complete(d.getId(), d.getCourierId(), "Route", "Proof"));
        var rolledBack = repository.findById(d.getId()).orElseThrow();
        assertNull(rolledBack.getCompletedAt());
        assertNull(rolledBack.getRouteSummary());
        assertNull(rolledBack.getConfirmation());
        verifyNoInteractions(publisher);

        service.complete(d.getId(), d.getCourierId(), "Route", "Proof");
        service.complete(d.getId(), d.getCourierId(), "Route", "Proof");
        var order = inOrder(snapshots, publisher);
        order.verify(snapshots, times(2)).publish(any(Delivery.class));
        order.verify(publisher).publish(any());
        verifyNoMoreInteractions(snapshots, publisher);
    }

    @Test
    void restRejectsInvalidMissingAndConflictingRequests() throws Exception {
        var d = start();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/deliveries/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post("/api/deliveries/{id}/complete", d.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/deliveries/{id}/complete", d.getId())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"courierId":"%s","routeSummary":"Route","confirmation":"Proof"}
                    """.formatted(UUID.randomUUID()))).andExpect(status().isConflict());
        service.complete(d.getId(), d.getCourierId(), "Route", "Proof");
        mvc.perform(post("/api/deliveries/{id}/complete", d.getId())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"courierId":"%s","routeSummary":"Route","confirmation":"Changed"}
                    """.formatted(d.getCourierId()))).andExpect(status().isConflict());
    }
}
