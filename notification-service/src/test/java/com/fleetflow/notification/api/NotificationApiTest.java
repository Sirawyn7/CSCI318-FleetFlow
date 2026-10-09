package com.fleetflow.notification.api;

import com.fleetflow.notification.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({NotificationController.class, ExplanationController.class})
class NotificationApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService notifications;
    @MockitoBean ExplanationService explanations;
    @Test void inboxIsScopedAndPaginated() throws Exception {
        UUID customer = UUID.randomUUID(); when(notifications.inbox(customer, 0, 20)).thenReturn(List.of());
        mvc.perform(get("/notifications").param("customerId", customer.toString())).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(notifications).inbox(customer, 0, 20);
        mvc.perform(get("/notifications")).andExpect(status().isBadRequest());
        mvc.perform(get("/notifications").param("customerId", "invalid")).andExpect(status().isBadRequest());
    }
    @Test void acceptsQuestionAndReturnsConversationId() throws Exception {
        UUID order = UUID.randomUUID(), conversation = UUID.randomUUID();
        when(explanations.explain(order, null, "Why late?")).thenReturn(
                new ExplanationService.Explanation(conversation, order, "ETA unavailable", true, null, null));
        mvc.perform(post("/orders/" + order + "/explanations").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Why late?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(conversation.toString()))
                .andExpect(jsonPath("$.fallback").value(true));
    }
    @Test void badQuestionsAndMissingOrdersHaveUsefulStatusCodes() throws Exception {
        UUID order = UUID.randomUUID();
        when(explanations.explain(order, null, "")).thenThrow(new IllegalArgumentException("Question required"));
        when(explanations.explain(order, null, "Status?")).thenThrow(new DeliveryDataPort.OrderNotFound());
        mvc.perform(post("/orders/" + order + "/explanations").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Question required"));
        mvc.perform(post("/orders/" + order + "/explanations").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Status?\"}"))
                .andExpect(status().isNotFound());
    }
}
