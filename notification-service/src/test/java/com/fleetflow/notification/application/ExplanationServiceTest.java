package com.fleetflow.notification.application;

import com.fleetflow.notification.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import(ExplanationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExplanationServiceTest {
    @Autowired ExplanationService service;
    @Autowired AgentConversationRepository conversations;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean DeliveryDataPort data;
    @MockitoBean StatusExplainerPort explainer;
    private final UUID order = UUID.randomUUID();
    @BeforeEach void setup() {
        conversations.deleteAll();
        when(data.order(order)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new DeliveryData.Order(order, UUID.randomUUID(), "IN_TRANSIT", Instant.now());
        });
        when(data.tracking(order)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new DeliveryData.Tracking(order, Instant.now(), List.of("Stationary"), Instant.now());
        });
        when(explainer.explain(anyString(), anyString(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            StatusContext context = call.getArgument(2); context.order(); context.tracking();
            return "Tracking reports the courier is stationary.";
        });
    }
    @Test void followUpPersistsHistoryAndRefreshesBothServices() {
        var first = service.explain(order, null, "Why is it late?");
        assertFalse(first.fallback());
        var second = service.explain(order, first.conversationId(), "What is the ETA?");
        assertEquals(first.conversationId(), second.conversationId());
        verify(explainer).explain(eq("What is the ETA?"), contains("Why is it late?"), any());
        verify(data, times(2)).order(order); verify(data, times(2)).tracking(order);
        int turnCount = new TransactionTemplate(transactionManager).execute(status ->
                conversations.findById(first.conversationId()).orElseThrow().getTurns().size());
        assertEquals(2, turnCount);
    }
    @Test void remoteExplanationSuspendsAnAmbientTransaction() {
        var result = new TransactionTemplate(transactionManager).execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return service.explain(order, null, "Status?");
        });
        assertFalse(result.fallback());
    }
    @Test void modelFailureAndMissingTrackingAreExplicitNotFabricated() {
        doThrow(new IllegalStateException()).when(explainer).explain(anyString(), anyString(), any());
        when(data.tracking(order)).thenThrow(new DeliveryDataPort.Unavailable("Tracking"));
        var result = service.explain(order, null, "Why delayed?");
        assertTrue(result.fallback()); assertTrue(result.answer().contains("Risk information is unavailable"));
        assertFalse(result.answer().contains("no risk flags"));
    }
    @Test void uncheckedAnswerFallsBackAndUnknownOrderDoesNotCreateConversation() {
        doReturn("It will arrive in five minutes!").when(explainer).explain(anyString(), anyString(), any());
        assertTrue(service.explain(order, null, "When?").fallback());
        long count = conversations.count();
        UUID missing = UUID.randomUUID(); when(data.order(missing)).thenThrow(new DeliveryDataPort.OrderNotFound());
        assertThrows(DeliveryDataPort.OrderNotFound.class, () -> service.explain(missing, null, "When?"));
        assertEquals(count, conversations.count());
    }
    @Test void rejectsInvalidQuestionsAndCrossOrderConversationReuse() {
        assertThrows(IllegalArgumentException.class, () -> service.explain(order, null, " "));
        assertThrows(IllegalArgumentException.class, () -> service.explain(order, null, "x".repeat(2001)));
        var first = service.explain(order, null, "Status?");
        assertThrows(ExplanationService.ConversationNotFound.class,
                () -> service.explain(UUID.randomUUID(), first.conversationId(), "Other order?"));
    }
}
