package com.fleetflow.dispatch.api;

import com.fleetflow.dispatch.application.*;
import com.fleetflow.dispatch.domain.Assignment;
import com.fleetflow.dispatch.infrastructure.persistence.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises application wiring and the persisted operator queue without substituting a Kafka broker. */
@SpringBootTest(properties = {
        "spring.cloud.stream.enabled=false", "spring.cloud.function.definition=",
        "spring.datasource.url=jdbc:h2:mem:fr9-api;DB_CLOSE_DELAY=-1",
        "dispatch.exceptions.sweep-ms=3600000"})
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext
class OperatorExceptionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AssignmentRepository assignments;
    @Autowired DeliveryExceptionService service;
    @MockitoBean AssignmentEventPort events;
    @MockitoBean DispatchAgent agent;

    @Test void queueExposesDurableEscalationAndCompletionRemovesIt() throws Exception {
        Instant time = Instant.now().minusSeconds(601);
        var a = Assignment.create(UUID.randomUUID(), UUID.randomUUID(), time);
        a.startDelivery(UUID.randomUUID()); a.recordException("NO_ELIGIBLE_COURIER", time);
        a.attemptException(time); a.escalateIfDue(time.plusSeconds(600)); assignments.save(a);
        mvc.perform(get("/api/dispatch/exceptions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].orderId").value(a.getOrderId().toString()))
                .andExpect(jsonPath("$[0].deliveryId").value(a.getDeliveryId().toString()))
                .andExpect(jsonPath("$[0].reason").value("NO_ELIGIBLE_COURIER"))
                .andExpect(jsonPath("$[0].attemptedAt").exists()).andExpect(jsonPath("$[0].escalatedAt").exists());
        service.closed(a.getOrderId(), a.getDeliveryId());
        mvc.perform(get("/api/dispatch/exceptions")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
