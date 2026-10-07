package com.fleetflow.notification.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "agent_conversations")
public class AgentConversation {
    @Id private UUID id;
    @Column(nullable = false) private UUID orderId;
    @Version private long version;
    @ElementCollection
    @CollectionTable(name = "agent_conversation_turns", joinColumns = @JoinColumn(name = "conversation_id"))
    @OrderColumn(name = "turn_index")
    private List<Turn> turns = new ArrayList<>();
    protected AgentConversation() { }
    public AgentConversation(UUID orderId) { this.id = UUID.randomUUID(); this.orderId = orderId; }
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public List<Turn> getTurns() { return List.copyOf(turns); }
    public String recentHistory() {
        return turns.stream().skip(Math.max(0, turns.size() - 3))
                .map(t -> "Customer: " + t.question + "\nAssistant: " + t.answer).reduce("", (a,b) -> a + "\n" + b);
    }
    public void append(String question, String answer, boolean fallback) { turns.add(new Turn(question, answer, fallback)); }
    @Embeddable
    public static class Turn {
        @Column(length = 2000, nullable = false) private String question;
        @Column(length = 8000, nullable = false) private String answer;
        private boolean fallback;
        private Instant createdAt;
        protected Turn() { }
        Turn(String question, String answer, boolean fallback) {
            this.question = question; this.answer = answer; this.fallback = fallback; this.createdAt = Instant.now();
        }
        public String getQuestion() { return question; }
        public String getAnswer() { return answer; }
        public boolean isFallback() { return fallback; }
        public Instant getCreatedAt() { return createdAt; }
    }
}
