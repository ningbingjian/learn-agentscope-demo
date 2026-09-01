package com.example.agentscope.consistency.model;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Model probe that makes overlapping model calls observable without any external API. */
public final class ConcurrentProbeModel implements Model {
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.defer(() -> {
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            return Mono.delay(Duration.ofMillis(120))
                    .map(ignored -> ChatResponse.builder()
                            .content(List.of(TextBlock.builder().text("ok").build()))
                            .usage(ChatUsage.builder().inputTokens(8).outputTokens(2).time(0.12).build())
                            .finishReason("stop")
                            .build())
                    .doFinally(signal -> active.decrementAndGet())
                    .flux();
        });
    }

    @Override
    public String getModelName() {
        return "lesson52-concurrency-probe";
    }

    public void reset() {
        active.set(0);
        maxActive.set(0);
    }

    public int maxActive() {
        return maxActive.get();
    }
}
