package com.fleetflow.dispatch.application;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.*;

/** Bounds the entire tool/model loop, not each individual HTTP call. */
@Component
public class BoundedDispatchAgent {
    private final DispatchAgent agent;
    private final Duration timeout;
    // No queue: an unresponsive provider cannot build an unbounded backlog of tasks.
    private final ExecutorService executor = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("dispatch-agent-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    @org.springframework.beans.factory.annotation.Autowired
    public BoundedDispatchAgent(DispatchAgent agent) { this(agent, Duration.ofSeconds(5)); }

    BoundedDispatchAgent(DispatchAgent agent, Duration timeout) {
        this.agent = agent;
        this.timeout = timeout;
    }

    public AgentAttempt attempt(DispatchContext context) {
        try {
            return new AgentAttempt(Deadline.await(() -> executor.submit(() -> agent.recommend(context)), timeout), null);
        } catch (RejectedExecutionException e) {
            return new AgentAttempt(null, "Agent busy");
        } catch (TimeoutException e) {
            return new AgentAttempt(null, "Agent timed out");
        } catch (ExecutionException e) {
            // Do not persist provider exception messages: these can contain URLs/API keys.
            return new AgentAttempt(null, "Agent unavailable or returned invalid output");
        } catch (InterruptedException e) {
            throw new IllegalStateException("Dispatch interrupted", e);
        }
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
