package com.fleetflow.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

class GpsPingSchedulerTest {
    @Test
    void postPingSendsALocationReadingToFleetsEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID courierId = UUID.randomUUID();

        server.expect(requestTo("http://fleet.test/api/couriers/" + courierId + "/location"))
                .andExpect(method(POST))
                .andRespond(withSuccess());

        GpsPingScheduler scheduler = new GpsPingScheduler(builder, "http://fleet.test", courierId);

        scheduler.postPing();

        server.verify();
    }

    @Test
    void postPingWalksFromThePreviousPositionRatherThanResettingNearZero() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID courierId = UUID.randomUUID();

        server.expect(requestTo("http://fleet.test/api/couriers/" + courierId + "/location"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.latitude", closeTo(-33.8688, 0.001)))
                .andExpect(jsonPath("$.longitude", closeTo(151.2093, 0.001)))
                .andRespond(withSuccess());

        GpsPingScheduler scheduler = new GpsPingScheduler(builder, "http://fleet.test", courierId);

        scheduler.postPing();

        server.verify();
    }
}
