package com.example.agentscope.observabilityandtracing.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

@Component
public class ObservabilityMetricsMiddleware implements MiddlewareBase {

    private final LongAdder agentCalls = new LongAdder();
    private final LongAdder successfulCalls = new LongAdder();
    private final LongAdder failedCalls = new LongAdder();
    private final LongAdder reasoningRounds = new LongAdder();
    private final LongAdder modelCalls = new LongAdder();
    private final LongAdder actingRounds = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong maxLatencyNanos = new AtomicLong();

    @Override
    public int order() {
        return 200;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next
    ) {
        agentCalls.increment();
        long started = System.nanoTime();
        return next.apply(input)
                .doOnComplete(successfulCalls::increment)
                .doOnError(error -> failedCalls.increment())
                .doFinally(signal -> recordLatency(System.nanoTime() - started));
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next
    ) {
        reasoningRounds.increment();
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next
    ) {
        modelCalls.increment();
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next
    ) {
        actingRounds.increment();
        return next.apply(input);
    }

    public Snapshot snapshot() {
        long calls = agentCalls.sum();
        long totalNanos = totalLatencyNanos.sum();
        return new Snapshot(
                calls,
                successfulCalls.sum(),
                failedCalls.sum(),
                reasoningRounds.sum(),
                modelCalls.sum(),
                actingRounds.sum(),
                nanosToMillis(totalNanos),
                calls == 0 ? 0.0 : nanosToMillis(totalNanos) / calls,
                nanosToMillis(maxLatencyNanos.get())
        );
    }

    public void reset() {
        agentCalls.reset();
        successfulCalls.reset();
        failedCalls.reset();
        reasoningRounds.reset();
        modelCalls.reset();
        actingRounds.reset();
        totalLatencyNanos.reset();
        maxLatencyNanos.set(0L);
    }

    private void recordLatency(long nanos) {
        totalLatencyNanos.add(nanos);
        maxLatencyNanos.accumulateAndGet(nanos, Math::max);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    public record Snapshot(
            long agentCalls,
            long successfulCalls,
            long failedCalls,
            long reasoningRounds,
            long modelCalls,
            long actingRounds,
            double totalLatencyMillis,
            double averageLatencyMillis,
            double maxLatencyMillis
    ) {}
}
