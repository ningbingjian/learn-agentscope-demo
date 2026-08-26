package com.example.agentscope.adminops.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

/** Local deterministic model so the control-plane lesson needs no API key. */
public final class AdminDemoModel implements Model {

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages,
            List<ToolSchema> tools,
            GenerateOptions options) {
        String input = messages == null || messages.isEmpty()
                ? ""
                : messages.get(messages.size() - 1).getTextContent();
        ContentBlock block = TextBlock.builder()
                .text("admin-demo reply: " + input)
                .build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(block))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return "lesson-admin-demo-model";
    }
}
