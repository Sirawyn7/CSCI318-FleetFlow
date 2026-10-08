package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.*;
import com.fleetflow.tracking.infrastructure.messaging.*;
import com.fleetflow.tracking.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.Message;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({DeliveryArrivalService.class, ActiveDeliverySnapshotPublisher.class, ActiveDeliverySnapshotRecovery.class})
class DeliveryArrivalPersistenceTest {
    @Autowired DeliveryJpaRepository deliveries;
    @Autowired CourierVehicleTypeViewRepository couriers;
    @Autowired DeliveryArrivalService arrivals;
    @Autowired ActiveDeliverySnapshotRecovery recovery;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired ActiveDeliverySnapshotPublisher publisher;
    @MockitoBean StreamBridge bridge;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void recoveryReleasesPreviousCourierLockWhileNextCourierIsStillPublishing() {
        UUID first = new UUID(0, 1), second = new UUID(0, 2);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            for (UUID id : java.util.List.of(first, second)) {
                couriers.save(new CourierVehicleTypeView(id, "VAN"));
                deliveries.save(Delivery.start(UUID.randomUUID(), id, new GeoCoordinate(-33.9, 151.2), 28));
            }
        });
        var acquiredDuringRecovery = new java.util.concurrent.atomic.AtomicBoolean();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            when(bridge.send(anyString(), any())).thenAnswer(call -> {
                Message<?> message = call.getArgument(1);
                if (second.toString().equals(message.getHeaders().get(org.springframework.kafka.support.KafkaHeaders.KEY))) {
                    // Simulate live bootstrap taking the first courier's lock while recovery holds the second.
                    var concurrent = executor.submit(() -> transaction.execute(status -> couriers.findForUpdate(first).isPresent()));
                    acquiredDuringRecovery.set(concurrent.get(5, java.util.concurrent.TimeUnit.SECONDS));
                }
                return true;
            });
            recovery.recover();
            assertTrue(acquiredDuringRecovery.get(), "Previous courier lock must be available before recovery finishes");
            verify(bridge, times(2)).send(eq("activeDeliverySnapshot-out-0"), any());
        } finally {
            transaction.executeWithoutResult(status -> {
                deliveries.deleteAll(deliveries.findAllIncludingArrivedByCourierId(first));
                deliveries.deleteAll(deliveries.findAllIncludingArrivedByCourierId(second));
                couriers.deleteById(first);
                couriers.deleteById(second);
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Test void arrivalPersistsAndLaterAssignmentsCannotRepublishHistoricalDelivery() {
        UUID courier = UUID.randomUUID();
        couriers.saveAndFlush(new CourierVehicleTypeView(courier, "VAN"));
        var old = deliveries.saveAndFlush(Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-33.9, 151.2), 28));
        when(bridge.send(anyString(), any())).thenReturn(true);
        Instant arrival = old.getStartedAt().plusSeconds(30);
        arrivals.arrive(old.getId(), arrival);
        assertEquals(arrival, deliveries.findById(old.getId()).orElseThrow().getArrivedAt());
        ArgumentCaptor<Message<CourierDeliveriesSnapshot>> messages = ArgumentCaptor.forClass(Message.class);
        verify(bridge).send(eq("activeDeliverySnapshot-out-0"), messages.capture());
        assertTrue(messages.getValue().getPayload().deliveries().isEmpty());
        var next = deliveries.saveAndFlush(Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-33.8, 151.2), 28));
        publisher.publish(next);
        arrivals.arrive(old.getId(), arrival.plusSeconds(1)); // redelivery remains idempotent
        verify(bridge, times(3)).send(eq("activeDeliverySnapshot-out-0"), messages.capture());
        var snapshot = messages.getValue().getPayload().deliveries();
        assertEquals(1, snapshot.size());
        assertEquals(next.getId(), snapshot.getFirst().deliveryId());
        assertEquals(arrival, deliveries.findById(old.getId()).orElseThrow().getArrivedAt());
        assertEquals(java.util.List.of(courier), deliveries.findCourierIds());
    }
}
