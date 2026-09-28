package com.fleetflow.dispatch.infrastructure.agent;

import dev.langchain4j.http.client.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeadlineHttpClientTest {
    @Test void configuredRuntimeTransportSupportsAsyncRequests() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                byte[] body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            var request = HttpRequest.builder().method(HttpMethod.GET)
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/").build();
            var response = new DeadlineHttpClient.Builder().build().execute(request);
            assertEquals(200, response.statusCode());
            assertEquals("ok", response.body());
        } finally { server.stop(0); }
    }

    @Test void unresponsiveTransportCannotOccupyTheAgentWorkerIndefinitely() throws Exception {
        var transport = mock(HttpClient.class);
        var request = mock(HttpRequest.class);
        // Represents I/O that ignores cancellation: never completes, even after cancel(true).
        var stuck = new CompletableFuture<SuccessfulHttpResponse>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
        };
        var healthy = SuccessfulHttpResponse.builder().statusCode(200).body("ok").build();
        when(transport.executeAsync(request)).thenReturn(stuck, CompletableFuture.completedFuture(healthy));
        var client = new DeadlineHttpClient(transport, Duration.ofMillis(100));
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> assertThrows(IllegalStateException.class, () -> client.execute(request)))
                    .get(2, TimeUnit.SECONDS);
            assertSame(healthy, worker.submit(() -> client.execute(request)).get(2, TimeUnit.SECONDS));
        }
        assertFalse(stuck.isDone());
        verify(transport, never()).execute(any());
    }

    @Test void interruptedWorkerCancelsPendingRequestAndExits() throws Exception {
        var transport = mock(HttpClient.class);
        var request = mock(HttpRequest.class);
        var pending = new CompletableFuture<SuccessfulHttpResponse>();
        var started = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        when(transport.executeAsync(request)).thenAnswer(invocation -> { started.countDown(); return pending; });
        var client = new DeadlineHttpClient(transport, Duration.ofSeconds(5));
        try (var worker = Executors.newSingleThreadExecutor()) {
            var task = worker.submit(() -> {
                try { client.execute(request); } finally { exited.countDown(); }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            task.cancel(true);
            assertTrue(exited.await(1, TimeUnit.SECONDS));
            assertTrue(pending.isCancelled());
        }
    }
}
