package com.fleetflow.notification.application;

import com.fleetflow.notification.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class ExplanationService {
    private final AgentConversationRepository conversations;
    private final DeliveryDataPort data;
    private final StatusExplainerPort explainer;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate commitTransaction;
    public ExplanationService(AgentConversationRepository conversations, DeliveryDataPort data,
                              StatusExplainerPort explainer, PlatformTransactionManager transactionManager) {
        this.conversations = conversations; this.data = data; this.explainer = explainer;
        readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        commitTransaction = new TransactionTemplate(transactionManager);
        commitTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Explanation explain(UUID orderId, UUID conversationId, String question) {
        if (question == null || question.isBlank() || question.length() > 2000)
            throw new IllegalArgumentException("Question must contain 1-2000 characters");
        ConversationState state = readTransaction.execute(status -> {
            var conversation = conversationId == null ? new AgentConversation(orderId)
                    : conversations.findById(conversationId).orElseThrow(ConversationNotFound::new);
            if (!conversation.getOrderId().equals(orderId)) throw new ConversationNotFound();
            if (conversation.getTurns().size() >= 50)
                throw new IllegalArgumentException("Conversation is full; start a new conversation");
            return new ConversationState(conversationId, conversation.recentHistory());
        });

        StatusContext.OrderFacts orderFacts;
        try {
            var order = data.order(orderId);
            if (order == null || !orderId.equals(order.id())) throw new DeliveryDataPort.Unavailable("Order");
            orderFacts = new StatusContext.OrderFacts(true, order.status(), order.requestedDeliveryBy());
        } catch (DeliveryDataPort.Unavailable ex) {
            orderFacts = new StatusContext.OrderFacts(false, null, null);
        }

        var context = new StatusContext(data, orderId, orderFacts);
        String answer;
        boolean fallback = false;
        try {
            answer = explainer.explain(question, state.history(), context);
            if (!context.bothRead() || answer == null || answer.isBlank() || answer.length() > 4000)
                throw new IllegalStateException("Invalid explanation");
        } catch (RuntimeException ex) {
            answer = context.fallback(); fallback = true;
        }
        if (answer.length() > 8000) answer = answer.substring(0, 7997) + "...";
        String finalAnswer = answer;
        boolean finalFallback = fallback;
        UUID savedConversationId = commitTransaction.execute(status -> {
            var conversation = state.id() == null ? new AgentConversation(orderId)
                    : conversations.findById(state.id()).orElseThrow(ConversationNotFound::new);
            if (!conversation.getOrderId().equals(orderId)) throw new ConversationNotFound();
            if (conversation.getTurns().size() >= 50)
                throw new IllegalArgumentException("Conversation is full; start a new conversation");
            conversation.append(question, finalAnswer, finalFallback);
            conversations.saveAndFlush(conversation);
            return conversation.getId();
        });
        return new Explanation(savedConversationId, orderId, finalAnswer, finalFallback, context.order(), context.tracking());
    }
    private record ConversationState(UUID id, String history) { }
    public record Explanation(UUID conversationId, UUID orderId, String answer, boolean fallback,
                              StatusContext.OrderFacts order, StatusContext.TrackingFacts tracking) { }
    public static class ConversationNotFound extends RuntimeException { }
}
