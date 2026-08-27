package com.example.agentscope.a2aprotocol.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

/** Deterministic model used by the A2A server lesson. */
public final class A2aDemoModel implements Model {

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        String input = messages == null || messages.isEmpty()
                ? ""
                : messages.get(messages.size() - 1).getTextContent();
        ContentBlock block = TextBlock.builder()
                .text("A2A server reply: " + input)
                .build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(block))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return "a2a-demo-model";
    }
}
