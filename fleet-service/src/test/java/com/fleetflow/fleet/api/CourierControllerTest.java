package com.fleetflow.fleet.api;

import com.fleetflow.fleet.application.ChangeCourierAvailabilityCommand;
import com.fleetflow.fleet.application.CourierAvailabilityService;
import com.fleetflow.fleet.application.CourierLocationService;
import com.fleetflow.fleet.application.CourierManagementService;
import com.fleetflow.fleet.application.CourierNotFoundException;
import com.fleetflow.fleet.application.CourierRegistrationService;
import com.fleetflow.fleet.application.IngestCourierLocationCommand;
import com.fleetflow.fleet.domain.Courier;
import com.fleetflow.fleet.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @MockitoBean
    private CourierManagementService managementService;

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
    void goingOnShiftAfterDeactivationReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(availabilityService.changeAvailability(any(ChangeCourierAvailabilityCommand.class)))
                .thenThrow(new IllegalStateException("A deactivated courier cannot go on shift"));

        mockMvc.perform(patch("/api/couriers/{id}/availability", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A deactivated courier cannot go on shift"));
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

    @Test
    void returnsProfileAndLoad() throws Exception {
        Courier courier = Courier.register(VehicleType.VAN, 3, "A");
        when(managementService.get(courier.getId())).thenReturn(courier);
        mockMvc.perform(get("/api/couriers/" + courier.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeOrderCount").value(0))
                .andExpect(jsonPath("$.deactivated").value(false));
    }

    @Test
    void mapsMissingAndConflictingCouriers() throws Exception {
        UUID id = UUID.randomUUID();
        when(managementService.get(id)).thenThrow(new CourierNotFoundException(id));
        mockMvc.perform(get("/api/couriers/" + id)).andExpect(status().isNotFound());
        when(managementService.deactivate(id)).thenThrow(new IllegalStateException("Active orders"));
        mockMvc.perform(post("/api/couriers/" + id + "/deactivate")).andExpect(status().isConflict());
    }

    @Test
    void mapsInvalidProfile() throws Exception {
        UUID id = UUID.randomUUID();
        when(managementService.update(eq(id), any())).thenThrow(new IllegalArgumentException("Invalid capacity"));
        mockMvc.perform(put("/api/couriers/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleType\":\"VAN\",\"capacity\":0,\"operatingZone\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }
}