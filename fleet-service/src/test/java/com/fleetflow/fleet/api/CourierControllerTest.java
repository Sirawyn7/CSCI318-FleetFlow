package com.fleetflow.fleet.api;

import com.fleetflow.fleet.application.*;
import com.fleetflow.fleet.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourierControllerTest {
    private final CourierManagementService management = mock(CourierManagementService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CourierController(mock(CourierRegistrationService.class), management))
                .setControllerAdvice(new CourierExceptionHandler()).build();
    }

    @Test void returnsProfileAndLoad() throws Exception {
        Courier courier = Courier.register(VehicleType.VAN, 3, "A");
        when(management.get(courier.getId())).thenReturn(courier);
        mvc.perform(get("/api/couriers/" + courier.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeOrderCount").value(0))
                .andExpect(jsonPath("$.deactivated").value(false));
    }

    @Test void mapsMissingAndConflictingCouriers() throws Exception {
        UUID id = UUID.randomUUID();
        when(management.get(id)).thenThrow(new CourierNotFoundException(id));
        mvc.perform(get("/api/couriers/" + id)).andExpect(status().isNotFound());
        when(management.deactivate(id)).thenThrow(new IllegalStateException("Active orders"));
        mvc.perform(post("/api/couriers/" + id + "/deactivate")).andExpect(status().isConflict());
    }

    @Test void mapsInvalidProfile() throws Exception {
        UUID id = UUID.randomUUID();
        when(management.update(eq(id), any())).thenThrow(new IllegalArgumentException("Invalid capacity"));
        mvc.perform(put("/api/couriers/" + id).contentType("application/json")
                .content("{\"vehicleType\":\"VAN\",\"capacity\":0,\"operatingZone\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }
}
