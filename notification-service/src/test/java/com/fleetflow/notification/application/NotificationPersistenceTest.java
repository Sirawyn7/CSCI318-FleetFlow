package com.fleetflow.notification.application;

import com.fleetflow.notification.domain.NotificationKind;
import com.fleetflow.notification.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({NotificationService.class, NotificationDeliveryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationPersistenceTest {
    @Autowired NotificationService service;
    @Autowired NotificationDeliveryService delivery;
    @Autowired NotificationRepository notifications;
    @Autowired ProcessedEventRepository processed;
    @Autowired DeliveryOrderLinkRepository links;
    @MockitoBean DeliveryDataPort data;
    @MockitoBean NotificationAuditPort audit;
    private final UUID order = UUID.randomUUID(), customer = UUID.randomUUID(), deliveryId = UUID.randomUUID();
    @BeforeEach void setup() {
        notifications.deleteAll(); processed.deleteAll(); links.deleteAll();
        when(data.order(order)).thenReturn(new DeliveryData.Order(order, customer, "PLACED", null));
    }
    @Test void allFiveTemplatesReachOnlyTheIntendedInboxAndDuplicateIsIgnored() {
        for (var kind : NotificationKind.values()) {
            UUID event = UUID.randomUUID();
            receive(event, order, deliveryId, kind);
            receive(event, order, deliveryId, kind);
        }
        assertEquals(5, processed.count()); assertEquals(5, notifications.count());
        notifications.findAll().forEach(n -> delivery.deliver(n.getId()));
        var inbox = service.inbox(customer, 0, 20);
        assertEquals(5, inbox.size());
        assertTrue(inbox.stream().allMatch(n -> n.message().contains(order.toString())));
        assertTrue(service.inbox(UUID.randomUUID(), 0, 20).isEmpty());
        notifications.findAll().forEach(n -> { delivery.publishAudit(n.getId()); delivery.publishAudit(n.getId()); });
        verify(audit, times(5)).publish(any());
    }
    @Test void nearDestinationBeforeStartedIsDurableAndResolvesLater() {
        receive(UUID.randomUUID(), null, deliveryId, NotificationKind.NEAR_DESTINATION);
        UUID pending = notifications.findAll().getFirst().getId();
        delivery.deliver(pending);
        assertNull(notifications.findById(pending).orElseThrow().getSentAt());
        receive(UUID.randomUUID(), order, deliveryId, NotificationKind.IN_TRANSIT);
        delivery.deliver(pending);
        assertEquals(1, service.inbox(customer, 0, 20).size());
    }
    @Test void unavailableOrderRetriesAndAuditFailureDoesNotUndoInbox() {
        receive(UUID.randomUUID(), order, null, NotificationKind.ASSIGNED);
        UUID id = notifications.findAll().getFirst().getId();
        when(data.order(order)).thenThrow(new DeliveryDataPort.Unavailable("Order"));
        assertThrows(DeliveryDataPort.Unavailable.class, () -> delivery.deliver(id));
        assertNull(notifications.findById(id).orElseThrow().getSentAt());
        doReturn(new DeliveryData.Order(order, customer, "ASSIGNED", null)).when(data).order(order);
        delivery.deliver(id);
        doThrow(new IllegalStateException()).when(audit).publish(any());
        assertThrows(IllegalStateException.class, () -> delivery.publishAudit(id));
        assertEquals(1, service.inbox(customer, 0, 20).size());
        assertFalse(notifications.findById(id).orElseThrow().isAuditPublished());
        doNothing().when(audit).publish(any()); delivery.publishAudit(id);
        assertTrue(notifications.findById(id).orElseThrow().isAuditPublished());
    }
    @Test void conflictingMappingRollsBackTheEventClaimAndNotification() {
        receive(UUID.randomUUID(), order, deliveryId, NotificationKind.IN_TRANSIT);
        UUID badEvent = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> receive(badEvent, UUID.randomUUID(), deliveryId, NotificationKind.DELIVERED));
        assertFalse(processed.existsById(badEvent)); assertEquals(1, notifications.count());
    }
    private void receive(UUID event, UUID orderId, UUID delivery, NotificationKind kind) {
        service.receive(event, Instant.now(), "correlation", orderId, delivery, kind, "Stationary");
    }
}
