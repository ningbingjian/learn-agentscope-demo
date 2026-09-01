package com.example.agentscope.production.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/** Local metrics probe. Production should export OpenTelemetry/Micrometer metrics. */
public final class ProductionTelemetryMiddleware implements MiddlewareBase {
    private final AtomicInteger agentCalls = new AtomicInteger();
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final AtomicInteger actingCalls = new AtomicInteger();
    private final AtomicLong totalLatencyNanos = new AtomicLong();

    @Override
    public int order() { return 100; }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        agentCalls.incrementAndGet();
        long start = System.nanoTime();
        return next.apply(input).doFinally(signal -> totalLatencyNanos.addAndGet(System.nanoTime() - start));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext ctx, ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        modelCalls.incrementAndGet();
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent, RuntimeContext ctx, ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        actingCalls.incrementAndGet();
        return next.apply(input);
    }

    public Map<String, Object> snapshot() {
        int calls = agentCalls.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentCalls", calls);
        result.put("modelCalls", modelCalls.get());
        result.put("actingCalls", actingCalls.get());
        result.put("averageAgentLatencyMs", calls == 0 ? 0.0 : totalLatencyNanos.get() / 1_000_000.0 / calls);
        return result;
    }

    public void reset() {
        agentCalls.set(0);
        modelCalls.set(0);
        actingCalls.set(0);
        totalLatencyNanos.set(0);
    }
}
