package com.example.agentscope.middlewarelifecycle.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@Component
public class AgentExecutionLoggingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionLoggingMiddleware.class);

    private final AtomicLong agentCalls = new AtomicLong();
    private final AtomicLong reasoningCalls = new AtomicLong();
    private final AtomicLong actingCalls = new AtomicLong();
    private final AtomicLong modelCalls = new AtomicLong();
    private final AtomicLong systemPromptTransforms = new AtomicLong();

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next
    ) {
        agentCalls.incrementAndGet();
        long startedNanos = System.nanoTime();
        log.info("middleware onAgent before: agent={}, userId={}, sessionId={}",
                agentName(agent), userId(ctx), sessionId(ctx));

        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "middleware onAgent after: agent={}, elapsedMillis={}",
                        agentName(agent), elapsedMillis(startedNanos)))
                .doOnError(error -> log.warn(
                        "middleware onAgent error: agent={}, message={}",
                        agentName(agent), error.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next
    ) {
        reasoningCalls.incrementAndGet();
        long startedNanos = System.nanoTime();
        log.info("middleware onReasoning before: userId={}, sessionId={}",
                userId(ctx), sessionId(ctx));

        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "middleware onReasoning after: elapsedMillis={}",
                        elapsedMillis(startedNanos)))
                .doOnError(error -> log.warn(
                        "middleware onReasoning error: {}",
                        error.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next
    ) {
        actingCalls.incrementAndGet();
        long startedNanos = System.nanoTime();
        log.info("middleware onActing before: userId={}, sessionId={}",
                userId(ctx), sessionId(ctx));

        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "middleware onActing after: elapsedMillis={}",
                        elapsedMillis(startedNanos)))
                .doOnError(error -> log.warn(
                        "middleware onActing error: {}",
                        error.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next
    ) {
        modelCalls.incrementAndGet();
        long startedNanos = System.nanoTime();
        log.info("middleware onModelCall before: userId={}, sessionId={}",
                userId(ctx), sessionId(ctx));

        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "middleware onModelCall after: elapsedMillis={}",
                        elapsedMillis(startedNanos)))
                .doOnError(error -> log.warn(
                        "middleware onModelCall error: {}",
                        error.getMessage()));
    }

    @Override
    public Mono<String> onSystemPrompt(
            Agent agent,
            RuntimeContext ctx,
            String currentPrompt
    ) {
        systemPromptTransforms.incrementAndGet();
        log.info("middleware onSystemPrompt: userId={}, sessionId={}",
                userId(ctx), sessionId(ctx));
        return Mono.just(currentPrompt);
    }

    public LifecycleSnapshot snapshot() {
        return new LifecycleSnapshot(
                agentCalls.get(),
                reasoningCalls.get(),
                actingCalls.get(),
                modelCalls.get(),
                systemPromptTransforms.get()
        );
    }

    public void reset() {
        agentCalls.set(0);
        reasoningCalls.set(0);
        actingCalls.set(0);
        modelCalls.set(0);
        systemPromptTransforms.set(0);
    }

    private static String agentName(Agent agent) {
        return agent == null ? "unknown" : agent.getName();
    }

    private static String userId(RuntimeContext ctx) {
        return ctx == null ? null : ctx.getUserId();
    }

    private static String sessionId(RuntimeContext ctx) {
        return ctx == null ? null : ctx.getSessionId();
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    public record LifecycleSnapshot(
            long agentCalls,
            long reasoningCalls,
            long actingCalls,
            long modelCalls,
            long systemPromptTransforms
    ) {
    }
}
