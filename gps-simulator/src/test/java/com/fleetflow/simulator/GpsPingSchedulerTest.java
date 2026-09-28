package com.fleetflow.simulator;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
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
                .andExpect(jsonPath("$.latitude", closeToAnyNumericType(-33.8688, 0.001)))
                .andExpect(jsonPath("$.longitude", closeToAnyNumericType(151.2093, 0.001)))
                .andRespond(withSuccess());

        GpsPingScheduler scheduler = new GpsPingScheduler(builder, "http://fleet.test", courierId);

        scheduler.postPing();

        server.verify();
    }

    /**
     * Hamcrest's built-in closeTo() type-checks against Double specifically. The JSON
     * number parser underneath jsonPath()'s assertion inconsistently returns Double or
     * BigDecimal for the same field depending on the exact decimal digits of that run's
     * random value (GpsPingScheduler.postPing()'s random walk), so closeTo() rejects a
     * value purely on type roughly half the time even though it's always within
     * tolerance -- a flaky test, not a flaky implementation. Comparing via
     * Number.doubleValue() instead of a fixed type sidesteps that.
     */
    private static Matcher<Object> closeToAnyNumericType(double expected, double tolerance) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                return actual instanceof Number number && Math.abs(number.doubleValue() - expected) <= tolerance;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a numeric value within ").appendValue(tolerance)
                        .appendText(" of ").appendValue(expected);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                description.appendText("was ").appendValue(item)
                        .appendText(item == null ? "" : " (" + item.getClass().getName() + ")");
            }
        };
    }
}
