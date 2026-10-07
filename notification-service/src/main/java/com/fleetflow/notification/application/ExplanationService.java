package com.fleetflow.notification.application;

import com.fleetflow.notification.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ExplanationService {
    private final AgentConversationRepository conversations;
    private final DeliveryDataPort data;
    private final StatusExplainerPort explainer;
    public ExplanationService(AgentConversationRepository conversations, DeliveryDataPort data, StatusExplainerPort explainer) {
        this.conversations = conversations; this.data = data; this.explainer = explainer;
    }
    @Transactional
    public Explanation explain(UUID orderId, UUID conversationId, String question) {
        if (question == null || question.isBlank() || question.length() > 2000)
            throw new IllegalArgumentException("Question must contain 1-2000 characters");
        var conversation = conversationId == null ? new AgentConversation(orderId)
                : conversations.findById(conversationId).orElseThrow(ConversationNotFound::new);
        if (!conversation.getOrderId().equals(orderId)) throw new ConversationNotFound();
        if (conversation.getTurns().size() >= 50) throw new IllegalArgumentException("Conversation is full; start a new conversation");
        var context = new StatusContext(data, orderId);
        String answer;
        boolean fallback = false;
        try {
            answer = explainer.explain(question, conversation.recentHistory(), context);
            if (!context.bothRead() || answer == null || answer.isBlank() || answer.length() > 4000)
                throw new IllegalStateException("Invalid explanation");
        } catch (DeliveryDataPort.OrderNotFound ex) { throw ex; }
        catch (RuntimeException ex) { answer = context.fallback(); fallback = true; }
        if (answer.length() > 8000) answer = answer.substring(0, 7997) + "...";
        conversation.append(question, answer, fallback);
        conversations.saveAndFlush(conversation);
        return new Explanation(conversation.getId(), orderId, answer, fallback, context.order(), context.tracking());
    }
    public record Explanation(UUID conversationId, UUID orderId, String answer, boolean fallback,
                              StatusContext.OrderFacts order, StatusContext.TrackingFacts tracking) { }
    public static class ConversationNotFound extends RuntimeException { }
}
