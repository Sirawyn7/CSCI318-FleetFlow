package com.fleetflow.notification.application;

import com.fleetflow.notification.domain.NotificationKind;
import com.fleetflow.notification.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({NotificationService.class, NotificationDeliveryService.class,
        com.fleetflow.notification.infrastructure.messaging.LifecycleConsumers.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationPersistenceTest {
    @Autowired NotificationService service;
    @Autowired com.fleetflow.notification.infrastructure.messaging.LifecycleConsumers consumers;
    @Autowired NotificationDeliveryService delivery;
    @Autowired NotificationRepository notifications;
    @Autowired ProcessedEventRepository processed;
    @Autowired DeliveryOrderLinkRepository links;
    @Autowired NotificationTemplateRepository templates;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean DeliveryDataPort data;
    @MockitoBean NotificationAuditPort audit;
    private final UUID order = UUID.randomUUID(), customer = UUID.randomUUID(), deliveryId = UUID.randomUUID();
    @Test void templatesThatIncludeTheReasonNeverExposeInternalCodes() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (var kind : List.of(NotificationKind.REASSIGNED, NotificationKind.ASSIGNMENT_FAILED)) {
                var template = new NotificationTemplate(kind);
                org.springframework.test.util.ReflectionTestUtils.setField(template, "body",
                        "Order {orderId}. Reason: {reason}");
                templates.saveAndFlush(template);
            }
        });
        consumers.notifyReassigned().accept(new com.fleetflow.contracts.dispatch.OrderReassigned(
                new com.fleetflow.contracts.EventEnvelope(UUID.randomUUID(), "OrderReassigned", Instant.now(), "test"),
                order, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "COURIER_OFFLINE"));
        consumers.notifyAssignmentFailed().accept(new com.fleetflow.contracts.dispatch.AssignmentFailed(
                new com.fleetflow.contracts.EventEnvelope(UUID.randomUUID(), "AssignmentFailed", Instant.now(), "test"),
                order, "Agent timed out; no available courier with known location"));
        service.receive(UUID.randomUUID(), Instant.now(), "test", order, deliveryId, NotificationKind.AT_RISK, "SLA_BREACH");
        notifications.findAll().forEach(n -> delivery.deliver(n.getId()));

        var messages = service.inbox(customer, 0, 20).stream().map(NotificationService.InboxMessage::message).toList();
        assertEquals(3, messages.size());
        assertTrue(messages.contains("Order " + order + ". Reason: the original courier became unavailable"));
        assertTrue(messages.contains("Order " + order + ". Reason: there is a possible delay"));
        assertTrue(messages.contains("Order " + order + " may be delayed: the delivery is predicted to arrive after the requested time."));
        for (var leaked : List.of("COURIER_OFFLINE", "Agent timed out", "SLA_BREACH")) {
            assertTrue(messages.stream().noneMatch(m -> m.contains(leaked)), "message exposed " + leaked);
        }
    }
    @Test void cancelledOrdersMessagesAreSuppressedNotSent() {
        receive(UUID.randomUUID(), order, deliveryId, NotificationKind.NEAR_DESTINATION);
        UUID id = notifications.findAll().getFirst().getId();
        doReturn(new DeliveryData.Order(order, customer, "CANCELLED", null)).when(data).order(order);

        delivery.deliver(id);
        delivery.deliver(id);

        var record = notifications.findById(id).orElseThrow();
        assertNull(record.getSentAt());
        assertNotNull(record.getSuppressedAt());
        assertTrue(service.inbox(customer, 0, 20).isEmpty());
        assertTrue(notifications.findNextPending(null, null, PageRequest.of(0, 25)).isEmpty(), "worker would retry it");
        delivery.publishAudit(id);
        verify(audit, never()).publish(any());
    }
    @Test void assignmentOutcomeConsumersHideDiagnosticsWithoutADeliveryLink() {
        var reassigned = new com.fleetflow.contracts.dispatch.OrderReassigned(
                new com.fleetflow.contracts.EventEnvelope(UUID.randomUUID(), "OrderReassigned", Instant.now(), "test"),
                order, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "COURIER_OFFLINE");
        var failed = new com.fleetflow.contracts.dispatch.AssignmentFailed(
                new com.fleetflow.contracts.EventEnvelope(UUID.randomUUID(), "AssignmentFailed", Instant.now(), "test"),
                order, "Agent timed out; no available courier with known location, remaining capacity and a matching zone");
        consumers.notifyReassigned().accept(reassigned);
        consumers.notifyReassigned().accept(reassigned);
        consumers.notifyAssignmentFailed().accept(failed);
        consumers.notifyAssignmentFailed().accept(failed);
        notifications.findAll().forEach(n -> delivery.deliver(n.getId()));
        var inbox = service.inbox(customer, 0, 20);
        assertEquals(2, inbox.size());
        assertTrue(inbox.stream().anyMatch(n -> n.kind() == NotificationKind.REASSIGNED && n.message().equals("A different courier has been assigned to order " + order + ".")));
        assertTrue(inbox.stream().anyMatch(n -> n.kind() == NotificationKind.ASSIGNMENT_FAILED && n.message().equals("We could not assign a courier to order " + order + ".")));
    }
    @BeforeEach void setup() {
        notifications.deleteAll(); processed.deleteAll(); links.deleteAll();
        when(data.order(order)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new DeliveryData.Order(order, customer, "PLACED", null);
        });
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(audit).publish(any());
    }
    @Test void allTemplatesReachOnlyTheIntendedInboxAndDuplicateIsIgnored() {
        for (var kind : NotificationKind.values()) {
            UUID event = UUID.randomUUID();
            receive(event, order, deliveryId, kind);
            receive(event, order, deliveryId, kind);
        }
        assertEquals(7, processed.count()); assertEquals(7, notifications.count());
        notifications.findAll().forEach(n -> delivery.deliver(n.getId()));
        var inbox = service.inbox(customer, 0, 20);
        assertEquals(7, inbox.size());
        assertTrue(inbox.stream().allMatch(n -> n.message().contains(order.toString())));
        assertTrue(service.inbox(UUID.randomUUID(), 0, 20).isEmpty());
        notifications.findAll().forEach(n -> { delivery.publishAudit(n.getId()); delivery.publishAudit(n.getId()); });
        verify(audit, times(7)).publish(any());
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
    @Test void deliveryAndAuditSuspendAnAmbientTransactionForExternalCalls() {
        receive(UUID.randomUUID(), order, null, NotificationKind.ASSIGNED);
        UUID id = notifications.findAll().getFirst().getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            delivery.deliver(id);
            delivery.publishAudit(id);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        });
        assertTrue(notifications.findById(id).orElseThrow().isAuditPublished());
    }
    @Test void conflictingMappingRollsBackTheEventClaimAndNotification() {
        receive(UUID.randomUUID(), order, deliveryId, NotificationKind.IN_TRANSIT);
        UUID badEvent = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> receive(badEvent, UUID.randomUUID(), deliveryId, NotificationKind.DELIVERED));
        assertFalse(processed.existsById(badEvent)); assertEquals(1, notifications.count());
    }
    @Test void keysetCursorKeepsItsPlaceWhenEarlierPendingRowsResolve() {
        for (int i = 0; i < 30; i++) receive(UUID.randomUUID(), order, null, NotificationKind.IN_TRANSIT);

        var first = notifications.findNextPending(null, null, PageRequest.of(0, 25));
        assertEquals(25, first.size());
        var last = first.getLast();
        first.forEach(n -> delivery.deliver(n.getId()));

        var next = notifications.findNextPending(last.getCreatedAt(), last.getId(), PageRequest.of(0, 25));
        assertEquals(5, next.size());
        assertTrue(Collections.disjoint(first.stream().map(NotificationRecord::getId).toList(),
                next.stream().map(NotificationRecord::getId).toList()));
    }
    @Test void concurrentFirstDeliveriesRetryAfterTemplateInsertConflict() throws Exception {
        receive(UUID.randomUUID(), order, null, NotificationKind.ASSIGNED);
        receive(UUID.randomUUID(), order, null, NotificationKind.ASSIGNED);
        List<UUID> ids = notifications.findAll().stream().map(NotificationRecord::getId).toList();
        templates.deleteAll();
        var gate = new CyclicBarrier(2);
        when(data.order(order)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            gate.await(10, TimeUnit.SECONDS);
            return new DeliveryData.Order(order, customer, "PLACED", null);
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> delivery.deliver(ids.get(0)));
            var second = executor.submit(() -> delivery.deliver(ids.get(1)));
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertTrue(templates.findById(NotificationKind.ASSIGNED.name()).isPresent());
        assertTrue(ids.stream().map(id -> notifications.findById(id).orElseThrow())
                .allMatch(n -> n.getSentAt() != null));
    }
    private void receive(UUID event, UUID orderId, UUID delivery, NotificationKind kind) {
        service.receive(event, Instant.now(), "correlation", orderId, delivery, kind, "Stationary");
    }
}
