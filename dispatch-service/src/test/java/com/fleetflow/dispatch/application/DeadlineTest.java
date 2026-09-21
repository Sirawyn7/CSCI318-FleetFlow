package com.fleetflow.dispatch.application;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class DeadlineTest {
    @Test void timeoutCancelsPendingWork() {
        var pending = new CompletableFuture<String>();
        assertThrows(TimeoutException.class, () -> Deadline.await(() -> pending, Duration.ofMillis(10)));
        assertTrue(pending.isCancelled());
    }

    @Test void interruptionCancelsWorkAndPreservesInterruptFlag() throws Exception {
        var started = new CountDownLatch(1);
        var pending = new CompletableFuture<String>();
        var preserved = new AtomicBoolean();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var worker = new java.util.concurrent.atomic.AtomicReference<Thread>();
            var result = pool.submit(() -> {
                worker.set(Thread.currentThread());
                assertThrows(InterruptedException.class, () -> Deadline.await(() -> {
                    started.countDown();
                    return pending;
                }, Duration.ofSeconds(5)));
                preserved.set(Thread.currentThread().isInterrupted());
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            worker.get().interrupt();
            result.get(2, TimeUnit.SECONDS);
        }
        assertTrue(pending.isCancelled());
        assertTrue(preserved.get());
    }

    @Test void alreadyInterruptedCallerDoesNotStartWork() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> {
                Thread.currentThread().interrupt();
                assertThrows(InterruptedException.class, () -> Deadline.await(() -> {
                    fail("Cancelled work must not be submitted");
                    return CompletableFuture.completedFuture("unexpected");
                }, Duration.ofSeconds(1)));
                assertTrue(Thread.currentThread().isInterrupted());
            }).get(2, TimeUnit.SECONDS);
        }
    }

    @Test void executionFailureRetainsItsCauseForCallerPolicy() {
        var cause = new IllegalArgumentException("provider failure");
        var failure = assertThrows(ExecutionException.class, () -> Deadline.await(
                () -> CompletableFuture.failedFuture(cause), Duration.ofSeconds(1)));
        assertSame(cause, failure.getCause());
    }
}
