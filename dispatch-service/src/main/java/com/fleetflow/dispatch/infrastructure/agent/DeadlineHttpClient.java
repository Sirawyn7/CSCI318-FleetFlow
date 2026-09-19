package com.fleetflow.dispatch.infrastructure.agent;

import dev.langchain4j.http.client.*;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.time.Duration;
import java.util.concurrent.*;

/** Wait on asynchronous I/O with a deadline; agent workers never perform blocking socket I/O. */
final class DeadlineHttpClient implements HttpClient {
    private final HttpClient delegate;
    private final Duration timeout;

    DeadlineHttpClient(HttpClient delegate, Duration timeout) {
        this.delegate = delegate;
        this.timeout = timeout;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Dispatch request cancelled");
        CompletableFuture<SuccessfulHttpResponse> response = delegate.executeAsync(request);
        try {
            return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dispatch request cancelled", e);
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new IllegalStateException("Dispatch HTTP deadline exceeded", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("Dispatch HTTP request failed", e.getCause());
        }
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        throw new UnsupportedOperationException("Dispatch uses non-streaming requests");
    }

    static final class Builder implements HttpClientBuilder {
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        @Override public Duration connectTimeout() { return connectTimeout; }
        @Override public Duration readTimeout() { return readTimeout; }
        @Override public Builder connectTimeout(Duration timeout) { connectTimeout = timeout; return this; }
        @Override public Builder readTimeout(Duration timeout) { readTimeout = timeout; return this; }
        @Override public HttpClient build() {
            // Gemini supplies the JDK async transport at runtime through this loader.
            return new DeadlineHttpClient(HttpClientBuilderLoader.loadHttpClientBuilder()
                    .connectTimeout(connectTimeout).readTimeout(readTimeout).build(), readTimeout);
        }
    }
}
