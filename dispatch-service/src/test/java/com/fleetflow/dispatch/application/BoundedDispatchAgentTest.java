package com.fleetflow.dispatch.application;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BoundedDispatchAgentTest {
    private final DispatchContext context = new DispatchContext(UUID.randomUUID(), Instant.now(), List.of(), List.of());

    @Test void wholeLoopTimesOutAndCancelsWorker() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        BoundedDispatchAgent bounded = new BoundedDispatchAgent(c -> {
            try { Thread.sleep(5000); }
            catch (InterruptedException e) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return new AgentRecommendation(c.targetOrderId(), UUID.randomUUID(), "Too late");
        }, Duration.ofMillis(100));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                AgentAttempt result = bounded.attempt(context);
                assertNull(result.recommendation());
                assertEquals("Agent timed out", result.fallbackReason());
            });
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally { bounded.close(); }
    }

    @Test void providerErrorBecomesSanitizedFallback() {
        BoundedDispatchAgent bounded = new BoundedDispatchAgent(c -> { throw new IllegalStateException("secret-api-key"); });
        try {
            AgentAttempt result = bounded.attempt(context);
            assertNull(result.recommendation());
            assertFalse(result.fallbackReason().contains("secret"));
        } finally { bounded.close(); }
    }

    @Test void successfulRecommendationIsReturned() {
        var recommendation = new AgentRecommendation(context.targetOrderId(), UUID.randomUUID(), "Suitable capacity");
        BoundedDispatchAgent bounded = new BoundedDispatchAgent(c -> recommendation);
        try { assertEquals(recommendation, bounded.attempt(context).recommendation()); }
        finally { bounded.close(); }
    }
}
