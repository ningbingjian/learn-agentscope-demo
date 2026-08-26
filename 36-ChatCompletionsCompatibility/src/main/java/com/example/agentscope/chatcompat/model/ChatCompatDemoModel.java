package com.example.agentscope.chatcompat.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

/** Deterministic local model used by the OpenAI Chat Completions compatibility lesson. */
public final class ChatCompatDemoModel implements Model {

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        String input = messages == null || messages.isEmpty()
                ? ""
                : messages.get(messages.size() - 1).getTextContent();
        ContentBlock block = TextBlock.builder()
                .text("Chat Completions demo reply: " + input)
                .build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(block))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return "chat-completions-demo-model";
    }
}
