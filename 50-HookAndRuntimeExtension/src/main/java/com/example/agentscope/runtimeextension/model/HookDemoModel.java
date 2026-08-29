package com.example.agentscope.runtimeextension.model;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;

public final class HookDemoModel implements Model {
    private final AtomicReference<String> lastSystemPrompt = new AtomicReference<>("");

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        if (messages != null) {
            messages.stream()
                    .filter(msg -> msg.getRole() == MsgRole.SYSTEM)
                    .findFirst()
                    .ifPresent(msg -> lastSystemPrompt.set(msg.getTextContent()));
        }
        return Flux.just(ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("runtime-extension-ok").build()))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() { return "hook-demo-model"; }

    public String lastSystemPrompt() { return lastSystemPrompt.get(); }
}
