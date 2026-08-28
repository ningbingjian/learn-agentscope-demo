package com.example.agentscope.runtimeextension.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Preferred 2.0 runtime extension mechanism. */
public final class CountingMiddleware implements MiddlewareBase {
    private final AtomicInteger agentCalls = new AtomicInteger();
    private final AtomicInteger reasoningCalls = new AtomicInteger();
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final AtomicInteger systemPromptCalls = new AtomicInteger();

    @Override
    public int order() { return 10; }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        agentCalls.incrementAndGet();
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext ctx, ReasoningInput input,
                                        Function<ReasoningInput, Flux<AgentEvent>> next) {
        reasoningCalls.incrementAndGet();
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        modelCalls.incrementAndGet();
        return next.apply(input);
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        systemPromptCalls.incrementAndGet();
        return Mono.just(currentPrompt + "\n[middleware-marker]");
    }

    public Map<String, Integer> snapshot() {
        return Map.of(
                "agent", agentCalls.get(),
                "reasoning", reasoningCalls.get(),
                "modelCall", modelCalls.get(),
                "systemPrompt", systemPromptCalls.get());
    }

    public void reset() {
        agentCalls.set(0); reasoningCalls.set(0); modelCalls.set(0); systemPromptCalls.set(0);
    }
}
