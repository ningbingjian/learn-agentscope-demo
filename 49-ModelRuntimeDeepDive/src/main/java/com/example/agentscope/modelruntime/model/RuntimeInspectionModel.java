package com.example.agentscope.modelruntime.model;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;

/** Deterministic model that exposes exactly what ReActAgent passes into Model.stream(). */
public final class RuntimeInspectionModel implements Model {
    private final AtomicReference<GenerateOptions> lastOptions = new AtomicReference<>();
    private final AtomicReference<List<String>> lastBlockTypes = new AtomicReference<>(List.of());
    private final AtomicInteger lastToolCount = new AtomicInteger();

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        lastOptions.set(options);
        lastToolCount.set(tools == null ? 0 : tools.size());
        List<String> blockTypes = messages == null
                ? List.of()
                : messages.stream()
                        .flatMap(msg -> msg.getContent().stream())
                        .map(block -> block.getClass().getSimpleName())
                        .toList();
        lastBlockTypes.set(blockTypes);

        return Flux.just(ChatResponse.builder()
                .content(List.of(
                        ThinkingBlock.builder().thinking("model runtime inspected").build(),
                        TextBlock.builder().text("runtime-ok").build()))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return "runtime-inspection-model";
    }

    @Override
    public int getContextWindowSize() {
        return 128_000;
    }

    public GenerateOptions lastOptions() {
        return lastOptions.get();
    }

    public List<String> lastBlockTypes() {
        return lastBlockTypes.get();
    }

    public int lastToolCount() {
        return lastToolCount.get();
    }
}
