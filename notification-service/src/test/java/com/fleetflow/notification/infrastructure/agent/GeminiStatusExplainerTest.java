package com.fleetflow.notification.infrastructure.agent;

import com.fleetflow.notification.application.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiStatusExplainerTest {
    private final UUID order = UUID.randomUUID();
    private final DeliveryDataPort data = mock(DeliveryDataPort.class);
    private StatusContext context() {
        when(data.order(order)).thenReturn(new DeliveryData.Order(order, UUID.randomUUID(), "IN_TRANSIT", null));
        when(data.tracking(order)).thenReturn(new DeliveryData.Tracking(order, Instant.now(), List.of("Stationary"), Instant.now()));
        return new StatusContext(data, order);
    }
    @Test void actualAiServicesLoopCallsBothScopedTools() {
        ChatModel model = new ChatModel() {
            int calls;
            @Override public ChatResponse doChat(ChatRequest request) {
                if (calls++ == 0) {
                    assertEquals(2, request.toolSpecifications().size());
                    request.toolSpecifications().forEach(t -> assertTrue(t.parameters() == null || t.parameters().properties().isEmpty()));
                    return response(AiMessage.from(tool("orderState"), tool("trackingState")));
                }
                var results = request.messages().stream().filter(ToolExecutionResultMessage.class::isInstance)
                        .map(ToolExecutionResultMessage.class::cast).toList();
                assertEquals(2, results.size());
                assertTrue(results.stream().anyMatch(r -> r.text().contains("Stationary")));
                return response(AiMessage.from("Tracking reports a stationary courier; the ETA is an estimate."));
            }
        };
        assertTrue(new GeminiStatusExplainer(model).explain("Why late?", "", context()).contains("stationary"));
        verify(data).order(order); verify(data).tracking(order);
    }
    @Test void rejectsAnswerWithoutBothTools() {
        ChatModel model = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) { return response(AiMessage.from("Delivered!")); }
        };
        assertThrows(IllegalStateException.class, () -> new GeminiStatusExplainer(model).explain("Status?", "", context()));
    }
    @Test void repeatedToolCallsAreBoundedAndRestReadsAreCached() {
        ChatModel model = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                return response(AiMessage.from(tool("orderState"), tool("trackingState")));
            }
        };
        assertThrows(RuntimeException.class, () -> new GeminiStatusExplainer(model).explain("Status?", "", context()));
        verify(data).order(order); verify(data).tracking(order);
    }
    @Test void missingKeyTriggersFallbackPath() {
        assertThrows(IllegalStateException.class, () -> new GeminiStatusExplainer("", "unused").explain("Status?", "", context()));
    }
    private static ToolExecutionRequest tool(String name) { return ToolExecutionRequest.builder().id(name).name(name).arguments("{}").build(); }
    private static ChatResponse response(AiMessage message) { return ChatResponse.builder().aiMessage(message).build(); }
}
