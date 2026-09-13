package com.fleetflow.fleet.api;

import com.fleetflow.fleet.application.ChangeCourierAvailabilityCommand;
import com.fleetflow.fleet.application.CourierAvailabilityService;
import com.fleetflow.fleet.application.CourierLocationService;
import com.fleetflow.fleet.application.CourierRegistrationService;
import com.fleetflow.fleet.application.IngestCourierLocationCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourierController.class)
class CourierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourierRegistrationService registrationService;

    @MockitoBean
    private CourierAvailabilityService availabilityService;

    @MockitoBean
    private CourierLocationService locationService;

    @Test
    void changeAvailabilityReturns404WhenCourierNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(availabilityService.changeAvailability(any(ChangeCourierAvailabilityCommand.class)))
                .thenThrow(new NoSuchElementException("courier not found: " + unknownId));

        mockMvc.perform(patch("/api/couriers/{id}/availability", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ingestLocationReturns404WhenCourierNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(locationService.ingestLocation(any(IngestCourierLocationCommand.class)))
                .thenThrow(new NoSuchElementException("courier not found: " + unknownId));

        mockMvc.perform(post("/api/couriers/{id}/location", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": -33.87, \"longitude\": 151.21}"))
                .andExpect(status().isNotFound());
    }
}