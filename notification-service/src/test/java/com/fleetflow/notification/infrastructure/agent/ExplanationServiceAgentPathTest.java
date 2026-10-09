package com.fleetflow.notification.infrastructure.agent;

import com.fleetflow.notification.application.*;
import com.fleetflow.notification.infrastructure.persistence.AgentConversationRepository;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExplanationServiceAgentPathTest {
    @Autowired AgentConversationRepository conversations;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean DeliveryDataPort data;

    @Test void missingOrderIsRejectedBeforeTheRealGeminiExplainerCanCallItsTools() {
        UUID missingOrder = UUID.randomUUID();
        when(data.order(missingOrder)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            throw new DeliveryDataPort.OrderNotFound();
        });
        ChatModel model = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                fail("The model must not be called when the order does not exist");
                return null;
            }
        };
        var service = new ExplanationService(conversations, data,
                new GeminiStatusExplainer(model), transactionManager);

        assertThrows(DeliveryDataPort.OrderNotFound.class,
                () -> service.explain(missingOrder, null, "Where is my order?"));
        assertEquals(0, conversations.count());
        verify(data).order(missingOrder);
    }
}
