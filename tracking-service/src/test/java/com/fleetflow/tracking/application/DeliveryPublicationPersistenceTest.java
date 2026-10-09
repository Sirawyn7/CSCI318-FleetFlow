package com.fleetflow.tracking.application;

import com.fleetflow.tracking.domain.*;
import com.fleetflow.tracking.infrastructure.messaging.*;
import com.fleetflow.tracking.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({DeliveryBootstrapService.class, VehicleSpeedConfiguration.class,
        DeliveryCompletionService.class, ActiveDeliverySnapshotPublisher.class,
        ActiveDeliverySnapshotRecovery.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryPublicationPersistenceTest {
    @Autowired DeliveryBootstrapService bootstrap;
    @Autowired DeliveryCompletionService completion;
    @Autowired ActiveDeliverySnapshotRecovery recovery;
    @Autowired DeliveryJpaRepository deliveries;
    @Autowired CourierVehicleTypeViewRepository couriers;
    @Autowired OrderDestinationViewRepository destinations;
    @Autowired ProcessedEventRepository processed;
    @MockitoBean StreamBridge bridge;
    @MockitoBean DeliveryStartedEventPublisher started;
    @MockitoBean DeliveryCompletedEventPublisher completed;

    @Test
    void failedBootstrapSnapshotRollsBackAndRedeliveryStartsOnlyOneDelivery() {
        UUID courier = UUID.randomUUID(), order = UUID.randomUUID(), event = UUID.randomUUID();
        couriers.saveAndFlush(new CourierVehicleTypeView(courier, "VAN"));
        destinations.saveAndFlush(new OrderDestinationView(order, new GeoCoordinate(-34, 151), Instant.now()));
        when(bridge.send(eq("activeDeliverySnapshot-out-0"), any())).thenReturn(false, true);

        assertThrows(IllegalStateException.class, () -> bootstrap.bootstrap(event, "OrderAssigned", order, courier));
        assertTrue(deliveries.findByOrderIdOrderByStartedAtAsc(order).isEmpty());
        assertFalse(processed.existsById(event));
        verifyNoInteractions(started);

        bootstrap.bootstrap(event, "OrderAssigned", order, courier);
        bootstrap.bootstrap(event, "OrderAssigned", order, courier);
        assertEquals(1, deliveries.findByOrderIdOrderByStartedAtAsc(order).size());
        assertTrue(processed.existsById(event));
        var ordered = inOrder(bridge, started);
        ordered.verify(bridge, times(2)).send(eq("activeDeliverySnapshot-out-0"), any());
        ordered.verify(started).publish(any(Delivery.class));
        verify(bridge, times(2)).send(eq("activeDeliverySnapshot-out-0"), any());
        verifyNoMoreInteractions(started);
    }

    @SuppressWarnings("unchecked")
    @Test
    void completionKeepsOtherOrdersAndRecoveryDoesNotResurrectCompletedDelivery() {
        UUID courier = UUID.randomUUID();
        couriers.saveAndFlush(new CourierVehicleTypeView(courier, "VAN"));
        var first = deliveries.saveAndFlush(Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-34, 151), 30));
        var second = deliveries.saveAndFlush(Delivery.start(UUID.randomUUID(), courier, new GeoCoordinate(-34, 151), 30));
        when(bridge.send(anyString(), any())).thenReturn(true);

        completion.complete(first.getId(), courier, "Route", "Proof");
        ArgumentCaptor<Message<CourierDeliveriesSnapshot>> messages = ArgumentCaptor.forClass(Message.class);
        verify(bridge).send(eq("activeDeliverySnapshot-out-0"), messages.capture());
        assertEquals(java.util.List.of(second.getId()), messages.getValue().getPayload().deliveries()
                .stream().map(ActiveDeliverySnapshot::deliveryId).toList());
        assertNull(deliveries.findById(first.getId()).orElseThrow().getArrivedAt());

        completion.complete(second.getId(), courier, "Route", "Proof");
        clearInvocations(bridge);
        recovery.recover();
        verify(bridge, atLeastOnce()).send(eq("activeDeliverySnapshot-out-0"), messages.capture());
        var recovered = messages.getAllValues().stream()
                .filter(m -> courier.toString().equals(m.getHeaders().get(org.springframework.kafka.support.KafkaHeaders.KEY)))
                .reduce((a, b) -> b).orElseThrow();
        assertTrue(recovered.getPayload().deliveries().isEmpty());
    }
}
