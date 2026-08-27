package com.example.agentscope.aguiprotocol.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

/** A deterministic local model so protocol experiments never need a real model API. */
public final class AguiDemoModel implements Model {

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        String input = messages == null || messages.isEmpty()
                ? ""
                : messages.get(messages.size() - 1).getTextContent();
        ContentBlock content = TextBlock.builder()
                .text("AG-UI demo reply: " + input)
                .build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(content))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return "agui-demo-model";
    }
}
