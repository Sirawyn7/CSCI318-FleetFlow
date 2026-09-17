package com.fleetflow.dispatch.infrastructure.agent;

import com.fleetflow.dispatch.application.*;
import com.fleetflow.dispatch.domain.GeoCoordinate;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GeminiDispatchAgentTest {
    private final UUID orderId = UUID.randomUUID(), courierId = UUID.randomUUID();
    private final GeoCoordinate position = new GeoCoordinate(-34.4, 150.8);
    private final DispatchContext context = new DispatchContext(orderId, Instant.now(),
            List.of(new DispatchContext.OrderSnapshot(orderId, position, position, "EXPRESS", Instant.now(), Instant.now())),
            List.of(new DispatchContext.CourierSnapshot(courierId, "VAN", "ZONE_A", position, 3, 1, 0, 2, true)));

    @Test void actualAiServicesLoopCallsBothReadOnlyToolsAndParsesRecommendation() {
        ChatModel model = new ChatModel() {
            int calls;
            @Override public ChatResponse doChat(ChatRequest request) {
                if (calls++ == 0) {
                    assertEquals(2, request.toolSpecifications().size());
                    return response(AiMessage.from(tool("fleetState"), tool("openOrders")));
                }
                var results = request.messages().stream().filter(ToolExecutionResultMessage.class::isInstance)
                        .map(ToolExecutionResultMessage.class::cast).toList();
                assertEquals(2, results.size());
                assertTrue(results.stream().anyMatch(r -> r.text().contains("remainingCapacity")));
                assertTrue(results.stream().anyMatch(r -> r.text().contains("deliveryDeadline")));
                return response(AiMessage.from(json()));
            }
        };
        AgentRecommendation recommendation = new GeminiDispatchAgent(model).recommend(context);
        assertEquals(courierId, recommendation.courierId());
        assertEquals(orderId, recommendation.orderId());
        assertEquals("Two free slots and nearby pickup", recommendation.justification());
    }

    @Test void answerWithoutConsultingToolsIsRejected() {
        ChatModel model = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) { return response(AiMessage.from(json())); }
        };
        assertThrows(IllegalStateException.class, () -> new GeminiDispatchAgent(model).recommend(context));
    }

    @Test void missingApiKeyFailsImmediatelyForFallback() {
        assertThrows(IllegalStateException.class, () -> new GeminiDispatchAgent("", "unused").recommend(context));
    }

    @Test void malformedStructuredOutputIsRejected() {
        ChatModel model = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) { return response(AiMessage.from("not JSON")); }
        };
        assertThrows(RuntimeException.class, () -> new GeminiDispatchAgent(model).recommend(context));
    }

    @Test void endlessToolRequestsAreBounded() {
        ChatModel model = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                return response(AiMessage.from(tool("fleetState"), tool("openOrders")));
            }
        };
        assertThrows(RuntimeException.class, () -> new GeminiDispatchAgent(model).recommend(context));
    }

    private String json() {
        return "{\"orderId\":\"" + orderId + "\",\"courierId\":\"" + courierId
                + "\",\"justification\":\"Two free slots and nearby pickup\"}";
    }
    private static ToolExecutionRequest tool(String name) {
        return ToolExecutionRequest.builder().id(name).name(name).arguments("{}").build();
    }
    private static ChatResponse response(AiMessage message) { return ChatResponse.builder().aiMessage(message).build(); }
}
