package com.fleetflow.dispatch.application;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Shared cancellation and interrupt handling; callers retain their own error policy. */
public final class Deadline {
    private Deadline() { }

    public static <T> T await(Supplier<? extends Future<T>> start, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Already interrupted");
        Future<T> task = start.get();
        try {
            return task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
