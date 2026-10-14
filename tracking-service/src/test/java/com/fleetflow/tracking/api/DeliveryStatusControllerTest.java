package com.fleetflow.tracking.api;

import com.fleetflow.tracking.application.DeliveryStatusQueryService;
import com.fleetflow.tracking.application.DeliveryStatusQueryService.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryStatusController.class)
class DeliveryStatusControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DeliveryStatusQueryService queryService;

    private final UUID orderId = UUID.randomUUID();

    @Test
    void returnsTheContractShapeNotificationExpects() throws Exception {
        when(queryService.statusForOrder(orderId)).thenReturn(new DeliveryStatus(orderId,
                Instant.parse("2026-10-07T08:10:00Z"), java.util.List.of("STATIONARY"),
                Instant.parse("2026-10-07T07:55:00Z")));

        mockMvc.perform(get("/deliveries/order/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.estimatedArrival").value("2026-10-07T08:10:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-07T07:55:00Z"))
                .andExpect(jsonPath("$.riskFlags[0]").value("STATIONARY"));
    }

    @Test
    void unknownOrderIs404() throws Exception {
        when(queryService.statusForOrder(orderId))
                .thenThrow(new NoSuchElementException("No delivery for order " + orderId));

        mockMvc.perform(get("/deliveries/order/{id}/status", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No delivery for order " + orderId));
    }

    @Test
    void malformedOrderIdIs400() throws Exception {
        mockMvc.perform(get("/deliveries/order/not-a-uuid/status")).andExpect(status().isBadRequest());
    }
}